package sa.urtv.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A website added as an app (WebApps): like an installed web app on a phone, its icon and name on its own colour
 * first (the splash), then the site full screen, no address bar. Sign-ins stay (cookies kept). The remote works it
 * like a mouse (Cursor): the arrows move a pointer (faster while held, the page scrolls at the edges), OK is a real
 * tap where it points, so buttons and fields behave as under a finger and a field opens the keyboard. Back goes back
 * a page and leaves on the first.
 */
public class WebAppActivity extends Activity {
    private WebView web;
    private Cursor cursor;
    private View splash;
    private long shownAt;
    private final Handler h = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        WebApps.Site site = WebApps.get(this, String.valueOf(getIntent().getStringExtra("id")));
        if (site == null) { finish(); return; }
        float d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(site.background);
        web = new WebView(this);
        web.setBackgroundColor(site.background);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return false; }   // stay in the app
            @Override public void onPageFinished(WebView v, String url) {
                hideSplash();
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        cursor = new Cursor(this);
        root.addView(cursor, new FrameLayout.LayoutParams(-1, -1));

        // the splash: the icon, the name under it, on the site's own background colour
        LinearLayout sp = new LinearLayout(this);
        sp.setOrientation(LinearLayout.VERTICAL);
        sp.setGravity(Gravity.CENTER);
        sp.setBackgroundColor(site.background);
        ImageView icon = new ImageView(this);
        Bitmap bm = WebApps.iconBitmap(this, site.id);
        if (bm != null) icon.setImageBitmap(bm);
        sp.addView(icon, new LinearLayout.LayoutParams(Math.round(168 * d), Math.round(168 * d)));
        TextView name = new TextView(this);
        name.setText(site.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        name.setTextColor(dark(site.background) ? Color.WHITE : 0xFF111111);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Math.round(22 * d), 0, 0);
        try { name.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Medium.otf")); } catch (Exception ignored) { }
        sp.addView(name, new LinearLayout.LayoutParams(-2, -2));
        splash = sp;
        root.addView(sp, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        shownAt = SystemClock.elapsedRealtime();
        h.postDelayed(this::hideSplash, 8000);   // a slow site: show what has loaded
        web.loadUrl(site.url);
        web.requestFocus();
    }

    /** At least 1.2 s of splash, like a phone's, then it fades. */
    private void hideSplash() {
        if (splash == null) return;
        long wait = 1200 - (SystemClock.elapsedRealtime() - shownAt);
        if (wait > 0) { h.postDelayed(this::hideSplash, wait); return; }
        final View v = splash;
        splash = null;
        v.animate().alpha(0f).setDuration(350).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    private static boolean dark(int c) {
        return Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114 < 150;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            if (web != null && web.canGoBack()) { web.goBack(); return true; }
        }
        if (cursor != null && web != null && splash == null && cursor.handle(e, web)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();   // the sign-in survives a restart
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
