package sa.urtv.live;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

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
    private FrameLayout videos, frosts;
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
        frosts = new FrameLayout(this);   // between the video and the page: the blurred picture under a panel
        root.addView(frosts, new FrameLayout.LayoutParams(-1, -1));
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
            // every picture the page asks for (posters, logos) goes through the TV's own store
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                try { return "GET".equalsIgnoreCase(r.getMethod()) ? picture(r.getUrl()) : null; }
                catch (Throwable t) { return null; }
            }
        });
        new Thread(this::trimPictures).start();
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
        for (ImageView v : frostViews) { v.setVisibility(View.GONE); v.setImageDrawable(null); }
    }

    // ---- the glass over the video. The page's panels (the channels' list, the player's bar) lie over the video,
    // which the page cannot see, so its own blur shows nothing through them. Here the part of the picture under
    // each panel is copied small a few times a second, softened, and shown enlarged between the video and the
    // page: with the panel's tint over it, frosted glass. Nothing is shown when the picture cannot be copied.
    private final List<ImageView> frostViews = new ArrayList<>();
    private final List<float[]> frostRects = new ArrayList<>();   // x, y, w, h, corner: parts of the screen's size
    private int frostBusy;
    private final Runnable frostTick = new Runnable() {
        @Override public void run() {
            if (frostRects.isEmpty()) return;
            frostNow();
            h.postDelayed(this, 250);
        }
    };

    private void setFrost(String spec) {
        frostRects.clear();
        if (spec != null && !spec.isEmpty()) {
            for (String part : spec.split(";")) {
                String[] n = part.split(",");
                if (n.length < 5 || frostRects.size() >= 3) continue;
                try {
                    float[] f = new float[5];
                    for (int i = 0; i < 5; i++) f[i] = Float.parseFloat(n[i]);
                    frostRects.add(f);
                } catch (Exception ignored) { }
            }
        }
        h.removeCallbacks(frostTick);
        int w = frosts.getWidth(), ht = frosts.getHeight();
        while (frostViews.size() < frostRects.size()) {
            ImageView v = new ImageView(this);
            v.setScaleType(ImageView.ScaleType.FIT_XY);
            v.setVisibility(View.GONE);
            v.setFocusable(false);
            frosts.addView(v, new FrameLayout.LayoutParams(1, 1));
            frostViews.add(v);
        }
        for (int i = 0; i < frostViews.size(); i++) {
            ImageView v = frostViews.get(i);
            if (i >= frostRects.size() || w == 0) {   // the panel fades out: so does its glass
                if (v.getVisibility() == View.VISIBLE) v.animate().alpha(0f).setStartDelay(0).setDuration(120).withEndAction(() -> { v.setVisibility(View.GONE); v.setImageDrawable(null); }).start();
                continue;
            }
            v.animate().cancel();
            if (v.getVisibility() == View.VISIBLE) v.setAlpha(1f);
            float[] f = frostRects.get(i);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            lp.leftMargin = Math.round(f[0] * w); lp.topMargin = Math.round(f[1] * ht);
            lp.width = Math.max(1, Math.round(f[2] * w)); lp.height = Math.max(1, Math.round(f[3] * ht));
            v.setLayoutParams(lp);
            final float corner = f[4] * w;
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner); }
            });
            v.setClipToOutline(true);
        }
        if (!frostRects.isEmpty()) h.post(frostTick);   // at once: the glass is there as the panel comes
    }

    private void frostNow() {
        if (frostBusy > 0 || layout != 1 || views.isEmpty() || views.get(0).getVisibility() != View.VISIBLE) return;
        View sv = views.get(0).getVideoSurfaceView();
        if (!(sv instanceof SurfaceView) || sv.getWidth() == 0 || !((SurfaceView) sv).getHolder().getSurface().isValid()) return;
        int[] at = new int[2], base = new int[2];
        sv.getLocationInWindow(at); frosts.getLocationInWindow(base);
        int w = frosts.getWidth(), ht = frosts.getHeight();
        for (int i = 0; i < frostRects.size() && i < frostViews.size(); i++) {
            final float[] f = frostRects.get(i);
            final ImageView v = frostViews.get(i);
            // the panel's place inside the video's own view (a picture narrower than the screen has bars beside it)
            Rect src = new Rect(Math.round(f[0] * w) + base[0] - at[0], Math.round(f[1] * ht) + base[1] - at[1], 0, 0);
            src.right = src.left + Math.round(f[2] * w); src.bottom = src.top + Math.round(f[3] * ht);
            if (!src.intersect(0, 0, sv.getWidth(), sv.getHeight()) || src.width() < 16 || src.height() < 16) { v.setVisibility(View.GONE); continue; }
            Bitmap made = null;
            try { made = Bitmap.createBitmap(Math.max(8, src.width() / 24), Math.max(8, src.height() / 24), Bitmap.Config.ARGB_8888); }
            catch (Throwable ignored) { }
            if (made == null) continue;
            final Bitmap small = made;
            frostBusy++;
            try {
                PixelCopy.request((SurfaceView) sv, src, small, new PixelCopy.OnPixelCopyFinishedListener() {
                    @Override public void onPixelCopyFinished(int result) {
                        frostBusy = Math.max(0, frostBusy - 1);
                        if (result != PixelCopy.SUCCESS || !frostRects.contains(f)) return;
                        soften(small);
                        v.setImageBitmap(small);
                        if (v.getVisibility() != View.VISIBLE) { v.animate().cancel(); v.setAlpha(1f); v.setVisibility(View.VISIBLE); }
                    }
                }, h);
            } catch (Throwable t) { frostBusy = Math.max(0, frostBusy - 1); }
        }
    }

    /** a small picture softened in place: two passes of a mean over its neighbours, along rows then columns */
    private static void soften(Bitmap b) {
        int w = b.getWidth(), ht = b.getHeight(), r = 2;
        int[] px = new int[w * ht], out = new int[w * ht];
        b.getPixels(px, 0, w, 0, 0, w, ht);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < ht; y++) for (int x = 0; x < w; x++) {
                int sr = 0, sg = 0, sb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = x + k; if (xx < 0 || xx >= w) continue;
                    int c = px[y * w + xx]; sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255; n++;
                }
                out[y * w + x] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
            for (int y = 0; y < ht; y++) for (int x = 0; x < w; x++) {
                int sr = 0, sg = 0, sb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int yy = y + k; if (yy < 0 || yy >= ht) continue;
                    int c = out[yy * w + x]; sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255; n++;
                }
                px[y * w + x] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, ht);
    }

    // ---- the pictures kept on the TV. A poster comes from the provider's server at its full size (often
    // thousands of pixels for a card two hundred wide) and was asked for again at every start: slow to fetch and
    // heavy to draw. Here each picture is fetched once, made small, and kept; later it is read from the TV.
    private static final int PIC_W = 420;
    private java.io.File picDir() { java.io.File d = new java.io.File(getCacheDir(), "pictures"); d.mkdirs(); return d; }
    private java.io.File picFile(String url) { return new java.io.File(picDir(), Integer.toHexString(url.hashCode()) + "-" + url.length()); }
    private static WebResourceResponse none() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new java.io.ByteArrayInputStream(new byte[0]));
    }
    private static String kind(java.io.File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int b = in.read();
            return b == 0x89 ? "image/png" : b == 'G' ? "image/gif" : b == 'R' ? "image/webp" : "image/jpeg";
        } catch (Exception e) { return "image/jpeg"; }
    }

    private WebResourceResponse picture(Uri u) throws Exception {
        String scheme = u.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
        if ("urtv.local".equals(u.getHost())) {   // the owner's photo, from the home screen app (same signing key)
            try {
                java.io.InputStream in = getContentResolver().openInputStream(Uri.parse("content://sa.urtv.home.profile/photo"));
                return in == null ? none() : new WebResourceResponse("image/png", null, in);
            } catch (Throwable t) { return none(); }
        }
        String url = u.toString();
        java.io.File f = picFile(url);
        if (!f.isFile() || f.length() == 0) {
            byte[] raw;
            try { raw = fetchBytes(url, 8 * 1024 * 1024); } catch (Exception e) { return none(); }
            byte[] small = shrink(raw);
            if (small == null) return none();   // not a picture
            java.io.File tmp = new java.io.File(f.getPath() + "." + Thread.currentThread().getId());
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(tmp)) { o.write(small); }
            if (!tmp.renameTo(f)) tmp.delete();
            return new WebResourceResponse(small.length > 0 && (small[0] & 255) == 0x89 ? "image/png" : kindOf(small), null, new java.io.ByteArrayInputStream(small));
        }
        return new WebResourceResponse(kind(f), null, new java.io.FileInputStream(f));
    }
    private static String kindOf(byte[] b) { return b[0] == 'G' ? "image/gif" : b[0] == 'R' ? "image/webp" : "image/jpeg"; }

    /** a picture no wider than a card needs (kept as it came when already small); null when it is no picture */
    private static byte[] shrink(byte[] raw) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        if (o.outWidth <= PIC_W && raw.length < 200 * 1024) return raw;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= PIC_W) sample *= 2;
        o = new android.graphics.BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap b = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        if (b == null) return null;
        if (b.getWidth() > PIC_W) {
            Bitmap s = Bitmap.createScaledBitmap(b, PIC_W, Math.max(1, Math.round(b.getHeight() * (PIC_W / (float) b.getWidth()))), true);
            if (s != b) b.recycle();
            b = s;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(64 * 1024);
        if (b.hasAlpha()) b.compress(Bitmap.CompressFormat.PNG, 100, out); else b.compress(Bitmap.CompressFormat.JPEG, 84, out);
        b.recycle();
        return out.toByteArray();
    }

    private static byte[] fetchBytes(String url, int max) throws Exception {
        for (int hop = 0; hop < 4; hop++) {
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            try {
                con.setInstanceFollowRedirects(false);
                con.setConnectTimeout(10000); con.setReadTimeout(20000);
                con.setRequestProperty("User-Agent", "IPTVSmartersPlayer");
                int code = con.getResponseCode();
                if (code >= 300 && code < 400) {
                    String to = con.getHeaderField("Location");
                    if (to == null) throw new Exception("redirect");
                    url = new java.net.URL(new java.net.URL(url), to).toString();
                    continue;
                }
                if (code != 200) throw new Exception("HTTP " + code);
                java.io.InputStream in = con.getInputStream();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(128 * 1024);
                byte[] buf = new byte[32768]; int n;
                while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); if (b.size() > max) throw new Exception("too large"); }
                in.close();
                return b.toByteArray();
            } finally { con.disconnect(); }
        }
        throw new Exception("too many redirects");
    }

    /** the store stays under 250 MB: past that the pictures not seen for the longest go */
    private void trimPictures() {
        try {
            java.io.File[] fs = picDir().listFiles();
            if (fs == null) return;
            long total = 0;
            for (java.io.File f : fs) total += f.length();
            if (total < 250L * 1024 * 1024) return;
            java.util.Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < fs.length && total > 150L * 1024 * 1024; i++) { total -= fs[i].length(); fs[i].delete(); }
        } catch (Throwable ignored) { }
    }

    // ---- a key in the app is someone using the TV. The screens are one page: no focus moves between views, so
    // the screensaver's watch (IdleWatch, which sees focus and clicks through accessibility events) saw nothing
    // and came up while the lists were being browsed. Now each key says so, at most once every two seconds.
    private long lastActive;
    private void active() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastActive < 2000) return;
        lastActive = now;
        try { sendBroadcast(new Intent("sa.urtv.screensaver.ACTIVE").setPackage("sa.urtv.screensaver")); } catch (Exception ignored) { }
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled()) return;
            AccessibilityEvent ev = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SELECTED);
            ev.setPackageName(getPackageName());
            ev.setClassName(MainActivity.class.getName());
            ev.setEnabled(true);
            am.sendAccessibilityEvent(ev);
        } catch (Exception ignored) { }
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

        /** the panels now over the video ("x,y,w,h,corner;…" in parts of the screen; "" for none): glass under them */
        @JavascriptInterface public void frost(final String spec) { h.post(() -> setFrost(spec)); }

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

        /** is this picture already kept on the TV (then the page shows it at once, without its queue)? */
        @JavascriptInterface public boolean hasImg(String url) {
            try { java.io.File f = picFile(url); return f.isFile() && f.length() > 0; } catch (Exception e) { return false; }
        }

        /** is there a newer version? window.onUpdateInfo(latest, mine); latest is -1 when it cannot be read */
        @JavascriptInterface public void checkUpdate() {
            new Thread(() -> js("window.onUpdateInfo && onUpdateInfo(" + SelfUpdate.latest() + "," + versionCode() + ")")).start();
        }

        @JavascriptInterface public void update() {
            new Thread(() -> {
                String r = SelfUpdate.check(getApplicationContext(), "urtv-live.apk", true, false);
                js("window.onUpdate && onUpdate(" + JSONObject.quote(r) + ")");
            }).start();
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
        if (e.getAction() == KeyEvent.ACTION_DOWN) active();
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
        // off screen: a newer version installs now (it would wait while watching). On its own thread: leaving with
        // Back closes the app's request threads (onDestroy), which cut this check short and no update ever came.
        final android.content.Context app = getApplicationContext();
        new Thread(() -> SelfUpdate.check(app, "urtv-live.apk", false, false)).start();
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
        h.removeCallbacksAndMessages(null);
        if (web != null) { web.destroy(); web = null; }
        net.shutdownNow();
        super.onDestroy();
    }
}
