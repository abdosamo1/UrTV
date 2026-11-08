package sa.urtv.screensaver;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

/**
 * The screensaver over apps too. Android starts it only when nothing keeps the screen on, and most TV apps
 * (IPTV, Netflix, YouTube) keep it on even in their menus, so it came only on the home screen. This service
 * sees the remote in use (focus moving, clicks, screens opening, the volume; never the keys themselves) and,
 * after the TV's own idle delay with
 * no key and nothing playing, shows the screensaver (MainActivity, as the dream would). A video or the adhan
 * playing (AudioManager.isMusicActive) is watching, not idle: then it waits. So is a TV input on screen (HDMI: a
 * PlayStation played with its own controller and headphones sends no remote key and no sound through the TV),
 * or live TV: their apps are known by package (the window shown, from the accessibility events).
 * The owner turns it on once: TV settings → Accessibility → تلفزيونك (or optimize/saver.sh over-apps true).
 */
public class IdleWatch extends AccessibilityService {
    private final Handler h = new Handler(Looper.getMainLooper());
    private static final long MARGIN_MS = 15_000;
    /** the app on screen now (accessibility window events); for the checks and SaverReceiver's report */
    static volatile String front = "";

    // the TV's own inputs and live TV: HDMI (a console, a receiver), the tuner. Never covered.
    private static final String[] INPUTS = {
        "com.sony.dtv.tvx", "com.sony.dtv.tvinput", "com.sony.dtv.hdmi", "com.android.tv", "com.google.android.tv",
        "com.google.android.apps.tv.launcherx.inputs", "com.mediatek.wwtv", "com.mediatek.tvinput",
        "com.droidlogic.tvinput", "com.droidlogic.tv", "com.tcl.tvinput", "com.hisense.tv", "com.android.tv.settings.inputs"
    };

    static boolean isInput(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (String p : INPUTS) if (pkg.equals(p) || pkg.startsWith(p + ".")) return true;
        return pkg.contains("tvinput") || pkg.contains(".hdmi");
    }

    // every 20 s: sound playing (a match on IPTV, a film) or an input on screen counts as watching, like a key.
    // The screensaver comes only after the whole idle delay with neither: a short pause in a stream (buffering,
    // a quiet moment) does not start it, since it was playing 20 s before.
    private static final long TICK_MS = 20_000;
    private long lastKey = System.currentTimeMillis(), lastBusy = System.currentTimeMillis();
    private boolean shown;   // once per idle stretch: a key or sound arms it again

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            h.postDelayed(this, TICK_MS);
            long now = System.currentTimeMillis(), idle = delay(IdleWatch.this);
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if ((am != null && am.isMusicActive()) || isInput(front)) { lastBusy = now; shown = false; }
            if (idle <= 0 || shown || pm == null || !pm.isInteractive() || ScreenView.onScreen > 0) return;
            if (now - Math.max(lastKey, lastBusy) < idle) return;
            shown = true;
            try {
                startActivity(new Intent(IdleWatch.this, MainActivity.class)
                    .putExtra(MainActivity.IDLE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (Exception ignored) { }
        }
    };

    /** the TV's idle delay plus a margin (on the home screen the system's dream comes first); 0 = never */
    static long delay(Context c) {
        long ms = Prefs.idleMs(c);
        if (ms <= 0 || ms >= Integer.MAX_VALUE / 2) return 0;
        return ms + MARGIN_MS;
    }

    // The remote is seen through what it does, not its keys: filtering keys (flagRequestFilterKeyEvents) made
    // the TV deliver a held key once, so holding volume up raised it one step only. Now: a focus moving, a click,
    // a screen opening (accessibility events), or the volume changing (its broadcast) count as a key.
    private final android.content.BroadcastReceiver volume = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { touched(); }
    };

    private void touched() {
        lastKey = System.currentTimeMillis();   // the clock starts again
        shown = false;
    }

    @Override
    protected void onServiceConnected() {
        h.removeCallbacks(tick);
        h.postDelayed(tick, TICK_MS);
        try {   // the system's broadcast: Android 13+ wants it said that it comes from outside
            android.content.IntentFilter f = new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(volume, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(volume, f);
        }
        catch (Exception ignored) { }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        String pkg = e.getPackageName() == null ? "" : e.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;   // our own screens
        touched();                                  // focus, click, a new screen: someone is using the remote
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || pkg.isEmpty()) return;
        if (pkg.equals("com.android.systemui")) return;   // a volume bar is not the app on screen
        front = pkg;
    }
    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(volume); } catch (Exception ignored) { }
        super.onDestroy();
    }

    /** turned on in the TV's accessibility settings? */
    static boolean enabled(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(on)) return false;
        String me = c.getPackageName() + "/" + IdleWatch.class.getName(), shortMe = c.getPackageName() + "/.IdleWatch";
        for (String s : on.split(":")) if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(shortMe)) return true;
        return false;
    }
}
