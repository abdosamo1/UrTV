package sa.urtv.live;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The page (assets/live/index.html: the screens, the remote, the lists) over up to four Media3 players (one channel,
 * two or four at once). The page asks the portal through Live.call (Portal does the MAG part) and tells the
 * players what to play and where; the sound follows the tile the page marks.
 */
public class MainActivity extends Activity {
    private static final int VOICE = 7;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newFixedThreadPool(3);
    private WebView web;
    private FrameLayout videos;
    private final List<ExoPlayer> players = new ArrayList<>();
    private final List<PlayerView> views = new ArrayList<>();
    private int layout = 1, focused = 0;
    private Portal portal;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        portal = new Portal(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        videos = new FrameLayout(this);
        root.addView(videos, new FrameLayout.LayoutParams(-1, -1));
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);   // posters and logos from an http portal
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            // the page's own process may run out of memory on a huge list: without this the whole app closed.
            // Now the players stop and the app starts its screens again.
            @Override public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                stopAll();
                if (web == v) web = null;
                try { ((android.view.ViewGroup) v.getParent()).removeView(v); v.destroy(); } catch (Exception ignored) { }
                h.post(MainActivity.this::recreate);
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Live");
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        web.loadUrl("file:///android_asset/live/index.html");
        web.requestFocus();
    }

    private void js(final String code) { h.post(() -> { if (web != null) web.evaluateJavascript(code, null); }); }

    // ---- the players
    private ExoPlayer player(int i) {
        while (players.size() <= i) {
            final int tile = players.size();
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000).setReadTimeoutMs(30000);
            ExoPlayer p = new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
            p.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException e) {
                    js("window.onPlayerError && onPlayerError(" + tile + "," + JSONObject.quote(String.valueOf(e.getErrorCodeName())) + ")");
                }
                @Override public void onPlaybackStateChanged(int st) {
                    if (st == Player.STATE_ENDED) js("window.onEnded && onEnded(" + tile + ")");
                }
            });
            PlayerView v = new PlayerView(this);
            v.setUseController(false);
            v.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            v.setShutterBackgroundColor(Color.BLACK);
            v.setPlayer(p);
            v.setFocusable(false);
            v.setVisibility(View.GONE);
            videos.addView(v, new FrameLayout.LayoutParams(-1, -1));
            players.add(p);
            views.add(v);
        }
        return players.get(i);
    }

    /** the tiles' places: one full screen, two side by side, four in a square; the first on the right (Arabic) */
    private void place() {
        int w = videos.getWidth(), ht = videos.getHeight();
        if (w == 0) { h.postDelayed(this::place, 50); return; }
        int gap = Math.round(w * .004f);
        for (int i = 0; i < views.size(); i++) {
            PlayerView v = views.get(i);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            if (i >= layout) { v.setVisibility(View.GONE); continue; }
            if (layout == 1) { lp.width = w; lp.height = ht; lp.leftMargin = 0; lp.topMargin = 0; }
            else {
                int cw = (w - gap * 3) / 2;
                int ch = layout == 2 ? cw * 9 / 16 : (ht - gap * 3) / 2;
                int col = 1 - (i % 2), row = i / 2;
                lp.width = cw; lp.height = ch;
                lp.leftMargin = gap + col * (cw + gap);
                lp.topMargin = layout == 2 ? (ht - ch) / 2 : gap + row * (ch + gap);
            }
            v.setLayoutParams(lp);
        }
    }

    private void volumes() {
        for (int i = 0; i < players.size(); i++) players.get(i).setVolume(i == focused ? 1f : 0f);
    }

    private void stopAll() {
        for (int i = 0; i < players.size(); i++) {
            players.get(i).stop();
            players.get(i).clearMediaItems();
            views.get(i).setVisibility(View.GONE);
        }
    }

    /** what the page can ask (from its own thread: the players are touched on the main one) */
    final class Bridge {
        @JavascriptInterface public String config() {
            try {
                return new JSONObject().put("mac", portal.mac()).put("portal", portal.url()).put("root", portal.root())
                    .put("version", "3." + versionCode()).toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public void setPortal(String u) { portal.setUrl(u); }

        /** load.php?<query> on the portal; the answer comes to window.__done(id, ok, text) */
        @JavascriptInterface public void call(final int id, final String query) {
            net.execute(() -> {
                String out; boolean ok;
                try { out = query.equals("login") ? (portalLogin()) : portal.call(query); ok = true; }
                catch (Exception e) { out = e.getMessage() == null ? "خطأ" : e.getMessage(); ok = false; }
                js("window.__done(" + id + "," + ok + "," + JSONObject.quote(out) + ")");
            });
        }

        /** a GET of a long list (Xtream's films, series, channels: tens of thousands), only the given fields kept:
         *  the whole answer through the page took more memory than its process had, and the app closed */
        @JavascriptInterface public void getSlim(final int id, final String url, final String fields) {
            net.execute(() -> {
                String out; boolean ok;
                try {
                    String body = fetchText(url);
                    org.json.JSONArray a = new org.json.JSONArray(body.trim());
                    String[] keep = fields.split(",");
                    StringBuilder b = new StringBuilder(a.length() * 120).append('[');
                    for (int i = 0; i < a.length(); i++) {
                        org.json.JSONObject x = a.optJSONObject(i);
                        if (x == null) continue;
                        org.json.JSONObject y = new org.json.JSONObject();
                        for (String k : keep) if (x.has(k) && !x.isNull(k)) y.put(k, x.opt(k));
                        if (b.length() > 1) b.append(',');
                        b.append(y.toString());
                    }
                    out = b.append(']').toString(); ok = true;
                    cachePut(url, out);
                } catch (Exception e) { out = e.getMessage() == null ? "تعذّر الاتصال" : e.getMessage(); ok = false; }
                js("window.__done(" + id + "," + ok + "," + JSONObject.quote(out) + ")");
            });
        }

        /** a plain GET (Xtream's player_api.php): the page on file:// may not fetch another site itself */
        @JavascriptInterface public void get(final int id, final String url) {
            net.execute(() -> {
                String out; boolean ok;
                try {
                    out = fetchText(url); ok = true;
                    // sections and whole lists (M3U) are kept too
                    if (url.contains("categories") || url.contains("get.php") || url.matches("(?i).*\\.m3u8?(\\?.*)?$")) cachePut(url, out);
                } catch (Exception e) { out = e.getMessage() == null ? "تعذّر الاتصال" : e.getMessage(); ok = false; }
                js("window.__done(" + id + "," + ok + "," + JSONObject.quote(out) + ")");
            });
        }

        /** the kept copy of a list ("" if none) and its age in ms (-1 if none) */
        @JavascriptInterface public String cacheGet(String url) {
            java.io.File f = cacheFile(url);
            if (!f.isFile()) return "";
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return b.toString("UTF-8");
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public long cacheAge(String url) {
            java.io.File f = cacheFile(url);
            return f.isFile() ? System.currentTimeMillis() - f.lastModified() : -1;
        }
        @JavascriptInterface public void cacheClear() {
            java.io.File[] fs = new java.io.File(getFilesDir(), "lists").listFiles();
            if (fs != null) for (java.io.File f : fs) f.delete();
        }

        @JavascriptInterface public void play(final int tile, final String url) {
            h.post(() -> {
                ExoPlayer p = player(tile);
                views.get(tile).setVisibility(View.VISIBLE);
                place();
                p.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
                p.prepare();
                p.setPlayWhenReady(true);
                volumes();
            });
        }

        @JavascriptInterface public void playFrom(final int tile, final String url, final long ms) {
            play(tile, url);
            h.post(() -> { if (ms > 0) player(tile).seekTo(ms); });
        }

        @JavascriptInterface public void layout(final int n) {
            h.post(() -> {
                layout = n == 2 || n == 4 ? n : 1;
                for (int i = layout; i < players.size(); i++) { players.get(i).stop(); players.get(i).clearMediaItems(); }
                if (focused >= layout) focused = 0;
                place(); volumes();
            });
        }

        @JavascriptInterface public void focus(final int tile) { h.post(() -> { focused = tile; volumes(); }); }
        @JavascriptInterface public void stop() { h.post(MainActivity.this::stopAll); }
        @JavascriptInterface public void pause() { h.post(() -> { if (!players.isEmpty()) players.get(0).pause(); }); }
        @JavascriptInterface public void resume() { h.post(() -> { if (!players.isEmpty()) players.get(0).play(); }); }
        @JavascriptInterface public void seekTo(final long ms) { h.post(() -> { if (!players.isEmpty()) players.get(0).seekTo(Math.max(0, ms)); }); }

        /** the first tile's state, read by the page twice a second while a film plays */
        private volatile String state = "{}";
        @JavascriptInterface public String state() {
            h.post(() -> {
                if (players.isEmpty()) { state = "{}"; return; }
                ExoPlayer p = players.get(0);
                long d = p.getDuration();
                try {
                    state = new JSONObject().put("pos", p.getCurrentPosition()).put("dur", d == C.TIME_UNSET ? 0 : d)
                        .put("playing", p.isPlaying()).put("buffering", p.getPlaybackState() == Player.STATE_BUFFERING).toString();
                } catch (Exception ignored) { }
            });
            return state;
        }

        @JavascriptInterface public void voice(final String lang) {
            h.post(() -> {
                try {
                    startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en".equals(lang) ? "en-US" : "ar"), VOICE);
                } catch (Exception e) { js("window.onVoice && onVoice('')"); }
            });
        }

        @JavascriptInterface public void update() {
            net.execute(() -> {
                String r = SelfUpdate.check(MainActivity.this, "urtv-live.apk", true, false);
                js("window.onUpdate && onUpdate(" + JSONObject.quote(r) + ")");
            });
        }

        @JavascriptInterface public void exit() { h.post(MainActivity.this::finish); }
    }

    // ---- the lists kept on the TV: titles, pictures, ratings and sections, so the screens are full at once
    private java.io.File cacheFile(String url) {
        java.io.File d = new java.io.File(getFilesDir(), "lists");
        d.mkdirs();
        return new java.io.File(d, Integer.toHexString(url.hashCode()) + "-" + url.length() + ".json");
    }
    private void cachePut(String url, String text) {
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(cacheFile(url))) { o.write(text.getBytes("UTF-8")); }
        catch (Exception ignored) { }
    }

    private static String fetchText(String url) throws Exception {
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw new Exception("رابط غير صالح");
        java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        try {
            con.setConnectTimeout(15000); con.setReadTimeout(90000);
            con.setRequestProperty("User-Agent", "IPTVSmartersPlayer");
            int code = con.getResponseCode();
            if (code == 401 || code == 403 || code == 461 || code == 429 || code == 458)
                throw new Exception("رفض السيرفر الطلب (" + code + "): اتصالات أكثر من المسموح أو إيقاف مؤقت. أغلق التطبيقات الأخرى وانتظر قليلاً");
            if (code >= 400) throw new Exception("رفض السيرفر الطلب (" + code + ")");
            java.io.InputStream in = con.getInputStream();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return b.toString("UTF-8");
        } finally { con.disconnect(); }
    }

    private String portalLogin() throws Exception { portal.login(); return "ok"; }

    private long versionCode() { return SelfUpdate.mine(this); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != VOICE) return;
        String text = "";
        if (res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) text = r.get(0);
        }
        js("window.onVoice && onVoice(" + JSONObject.quote(text) + ")");
    }

    // ---- the remote: Back asks the page first; channel and media keys go to it by name
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        String name = null;
        switch (k) {
            case KeyEvent.KEYCODE_CHANNEL_UP: name = "ChannelUp"; break;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: name = "ChannelDown"; break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PAUSE:
                name = "PlayPause"; break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: name = "Forward"; break;
            case KeyEvent.KEYCODE_MEDIA_REWIND: name = "Rewind"; break;
            default: break;
        }
        if (name != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) js("window.remote && remote('" + name + "')");
            return true;
        }
        // a held key: its repeats are handed to the page here (the WebView let them stall after the first),
        // and a held OK is told as one long OK
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() > 0) {
            String arrow = k == KeyEvent.KEYCODE_DPAD_UP ? "ArrowUp" : k == KeyEvent.KEYCODE_DPAD_DOWN ? "ArrowDown"
                : k == KeyEvent.KEYCODE_DPAD_LEFT ? "ArrowLeft" : k == KeyEvent.KEYCODE_DPAD_RIGHT ? "ArrowRight" : null;
            if (arrow != null) { js("window.remote && remote('" + arrow + "', true)"); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                if (e.getRepeatCount() == 1) js("window.remote && remote('LongOK', true)");
                return true;
            }
        }
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && web != null) {
                web.evaluateJavascript("window.onBack ? onBack() : false", v -> { if (!"true".equals(v)) finish(); });
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        js("window.onLeave && onLeave()");   // the page keeps where a film stopped
        // the streams close as soon as the app is left: the server counts the connection gone at once, not
        // minutes later (two counted connections answer 461); the page saved the film's place first
        h.postDelayed(this::stopAll, 300);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopAll();
        for (ExoPlayer p : players) p.release();   // and their connections with them
        players.clear();
        for (PlayerView v : views) videos.removeView(v);
        views.clear();
        // off screen: a newer version installs now (it would wait while watching)
        net.execute(() -> SelfUpdate.check(this, "urtv-live.apk", false, false));
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        js("window.onReturn && onReturn()");
    }

    @Override
    protected void onDestroy() {
        for (ExoPlayer p : players) p.release();
        players.clear();
        if (web != null) { web.destroy(); web = null; }
        net.shutdownNow();
        super.onDestroy();
    }
}
