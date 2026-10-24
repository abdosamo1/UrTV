package sa.urtv.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Collections;

/**
 * The optional card on the home screen: the screensaver's page in card-only mode (#widget=1), showing
 * the panel picked in the screensaver's settings («بطاقة على الشاشة الرئيسية»): the four cards in turn unless
 * picked otherwise, in a glass frame; set to «لا شيء», no WebView is created. Settings come from the screensaver app's ConfigProvider (same signing key).
 */
final class HomeCard {
    private static final Uri CONFIG = Uri.parse("content://sa.urtv.screensaver.config");
    private static final String FONTS = "https://raw.githubusercontent.com/abdosamo1/UrTV/master/assets/fonts/";

    private final Activity activity;
    private final ViewGroup parent;
    private final FrameLayout.LayoutParams params;
    private WebView web;
    private FrameLayout frame;   // the glass the card sits in (Glass: the frosted wallpaper, a tint, gloss and rim)
    private String url;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int lastHeight = -1;

    /** The frame takes the card's own height (the four cards differ, and they take turns): asked every 1.5 s. */
    private final Runnable fit = new Runnable() {
        @Override public void run() {
            if (web == null || frame == null) return;
            web.evaluateJavascript("(function(){var w=document.querySelector('.week');"
                + "return w?Math.ceil(w.getBoundingClientRect().height):0;})()", v -> {
                    try {
                        int css = Integer.parseInt(v.replace("\"", "").trim());
                        if (css <= 0 || frame == null) return;
                        float d = activity.getResources().getDisplayMetrics().density;
                        int px = Math.round(Math.min(css + 22, 560) * d);   // + the frame's padding
                        if (px != lastHeight) {
                            lastHeight = px;
                            ViewGroup.LayoutParams lp = frame.getLayoutParams();
                            lp.height = px;
                            frame.setLayoutParams(lp);
                        }
                    } catch (Exception ignored) { }
                });
            handler.postDelayed(this, 1500);
        }
    };

    HomeCard(Activity activity, ViewGroup parent, FrameLayout.LayoutParams params) {
        this.activity = activity;
        this.parent = parent;
        this.params = params;
    }

    /** onResume: follow the current settings; a changed choice gets a fresh page. */
    void refresh() {
        String want = readUrl();
        if (want == null) { destroy(); return; }
        if (!want.equals(url)) { destroy(); create(want); }
        web.onResume();
        web.resumeTimers();
        handler.removeCallbacks(fit);
        handler.post(fit);
    }

    /** onPause: no timers or network while an app is in front. */
    void pause() {
        handler.removeCallbacks(fit);
        if (web != null) { web.onPause(); web.pauseTimers(); }
    }

    void destroy() {
        handler.removeCallbacks(fit);
        lastHeight = -1;
        if (web == null) return;
        parent.removeView(frame);
        frame = null;
        web.destroy();
        web = null;
        url = null;
    }

    private String readUrl() {
        try (Cursor c = activity.getContentResolver().query(CONFIG, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            String frag = c.getString(0), panel = c.getString(1);
            if (frag == null || panel == null || "none".equals(panel)) return null;
            frag = frag.replaceAll("(^|&)(panel|week)=[^&]*", "");   // the screensaver's own card choice
            return "file:///android_asset/web/tv.html#" + frag + "&panel=" + panel + "&widget=1";
        } catch (RuntimeException e) {
            return null;   // screensaver app missing or too old
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    private void create(String u) {
        web = new WebView(activity);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            // the page asks GitHub for its fonts; answer from the APK
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String s = r.getUrl().toString();
                if (s.startsWith(FONTS)) {
                    String name = s.substring(FONTS.length());
                    if (name.matches("[A-Za-z0-9_-]+\\.woff2")) {
                        try {
                            return new WebResourceResponse("font/woff2", null, 200, "OK",
                                Collections.singletonMap("Access-Control-Allow-Origin", "*"),
                                activity.getAssets().open("fonts/" + name));
                        } catch (java.io.IOException ignored) { }
                    }
                }
                return super.shouldInterceptRequest(v, r);
            }
        });
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // same as the screensaver: the page calls its data APIs directly from file://
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        float d = activity.getResources().getDisplayMetrics().density;
        frame = new FrameLayout(activity);
        frame.setBackground(new Glass(frame, 24 * d, 1.2f * d, 0x2EFFFFFF, true));
        frame.setClipToOutline(true);
        frame.setElevation(8 * d);
        int pad = Math.round(14 * d);
        frame.setPadding(pad, Math.round(10 * d), pad, Math.round(10 * d));
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        parent.addView(frame, new FrameLayout.LayoutParams(params));   // its own copy: the height changes
        url = u;
        web.loadUrl(u);
    }
}
