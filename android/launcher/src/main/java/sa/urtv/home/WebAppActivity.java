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
 * first (the splash), then the site full screen, no address bar. Sign-ins stay (cookies kept). The remote works it:
 * the arrows move between the page's links, buttons and fields (a small script, NAV, adds this to any site), OK
 * clicks, Back goes back a page and leaves on the first.
 */
public class WebAppActivity extends Activity {
    private WebView web;
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
                v.evaluateJavascript(NAV, null);
                hideSplash();
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

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

    /**
     * The remote on a site made for touch: ▲▼◀▶ move to the nearest link, button or field in that direction (and
     * scroll when there is none), OK clicks it, a gold ring shows where you are. Inside a text field the arrows
     * stay with the text, except ▲▼ which leave it. Run after every page load; safe to run twice.
     */
    static final String NAV = "(function(){if(window.__urtvNav)return;window.__urtvNav=1;"
        + "var st=document.createElement('style');st.textContent='.urtv-f{outline:3px solid #D6AF6B!important;"
        + "outline-offset:3px!important;border-radius:6px;box-shadow:0 0 0 6px rgba(214,175,107,.25)!important}';"
        + "document.head.appendChild(st);"
        + "var Q='a[href],button,input:not([type=hidden]),select,textarea,[role=button],[tabindex]:not([tabindex=\"-1\"]),[onclick],summary,label[for]';"
        + "function vis(e){var r=e.getBoundingClientRect();var s=getComputedStyle(e);"
        + "return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'&&!e.disabled;}"
        + "function all(){return Array.prototype.filter.call(document.querySelectorAll(Q),vis);}"
        + "var cur=null;function mark(e){if(cur)cur.classList.remove('urtv-f');cur=e;if(!e)return;e.classList.add('urtv-f');"
        + "try{e.focus({preventScroll:true});}catch(x){}e.scrollIntoView({block:'nearest',inline:'nearest',behavior:'smooth'});}"
        + "function c(r){return{x:r.left+r.width/2,y:r.top+r.height/2};}"
        + "function go(dir){var els=all();if(!els.length)return false;if(!cur||!document.contains(cur)||!vis(cur)){"
        + "var first=els.filter(function(e){var r=e.getBoundingClientRect();return r.top>=0&&r.top<innerHeight;})[0]||els[0];mark(first);return true;}"
        + "var ra=cur.getBoundingClientRect(),a=c(ra),best=null,bs=1e9;els.forEach(function(e){if(e===cur)return;var rb=e.getBoundingClientRect(),b=c(rb);"
        + "var dx=b.x-a.x,dy=b.y-a.y,main,side,v=dir==='ArrowDown'||dir==='ArrowUp';if(dir==='ArrowDown'){main=dy;side=dx;}else if(dir==='ArrowUp'){main=-dy;side=dx;}"
        + "else if(dir==='ArrowRight'){main=dx;side=dy;}else{main=-dx;side=dy;}if(main<=2)return;"
        // same column (or row): no sideways cost, so the next field down wins over a far one straight below
        + "var over=v?Math.min(ra.right,rb.right)-Math.max(ra.left,rb.left):Math.min(ra.bottom,rb.bottom)-Math.max(ra.top,rb.top);"
        + "var sc=main+(over>0?0:Math.abs(side)*2.5);"
        + "if(sc<bs){bs=sc;best=e;}});if(best){mark(best);return true;}return false;}"
        + "document.addEventListener('keydown',function(ev){var k=ev.key;var t=ev.target;"
        + "var typing=t&&(t.tagName==='TEXTAREA'||(t.tagName==='INPUT'&&!/^(checkbox|radio|button|submit|reset|range|file|color)$/i.test(t.type)));"
        + "if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].indexOf(k)>=0){if(typing&&(k==='ArrowLeft'||k==='ArrowRight'))return;"
        + "if(go(k)){ev.preventDefault();}else if(k==='ArrowDown'||k==='ArrowUp'){window.scrollBy({top:(k==='ArrowDown'?1:-1)*innerHeight*0.6,behavior:'smooth'});ev.preventDefault();}}"
        + "else if((k==='Enter'||k===' ')&&cur&&!typing&&cur.tagName!=='SELECT'){ev.preventDefault();cur.click();}},true);"
        + "setTimeout(function(){go('ArrowDown');},300);})();";
}
