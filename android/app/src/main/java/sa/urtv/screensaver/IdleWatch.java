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

    // ---- the volume: our wheel instead of the TV's bar (settings, on by default). Only the volume keys are taken:
    // every other key goes on as it came. A held key is repeated here (filtered keys come once), first after a
    // pause, then faster, as the TV's own would; the volume is set without the system's bar (no FLAG_SHOW_UI).
    static volatile IdleWatch self;
    private android.view.WindowManager wm;
    private VolumeWheel volView;
    private int heldKey = 0, heldSteps = 0;

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            if (heldKey == 0) return;
            stepVolume(heldKey);
            heldSteps++;
            h.postDelayed(this, heldSteps < 3 ? 160 : heldSteps < 10 ? 95 : 55);
        }
    };

    /** the settings switch: take the volume keys or leave them to the TV */
    void applyVolumeSetting() {
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        boolean on = new Prefs(this).volumeWheel();
        if (on) info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags &= ~android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        android.accessibilityservice.AccessibilityServiceInfo now = getServiceInfo();
        filterOn = on && now != null && (now.flags & android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
            && (now.getCapabilities() & android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0;
        if (!on) hideVolume();
    }

    static void settingChanged() { IdleWatch w = self; if (w != null) w.applyVolumeSetting(); }

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) keysSeen++;
        boolean vol = k == android.view.KeyEvent.KEYCODE_VOLUME_UP || k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            || k == android.view.KeyEvent.KEYCODE_VOLUME_MUTE;
        if (!vol && keysSeen >= 8 && volumeSeen == 0 && new Prefs(this).volumeWheel()) {
            // the TV keeps the volume keys to itself (they never come here): stop taking keys, leave its bar
            new Prefs(this).setVolumeWheelRefused();
            h.post(this::applyVolumeSetting);
            return false;
        }
        if (!vol || !new Prefs(this).volumeWheel()) return false;   // not ours: on to the app, untouched
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) volumeSeen++;
        AudioManager am0 = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am0 == null || am0.isVolumeFixed()) return false;        // the TV's volume is not ours to set (a fixed output)
        // answer the system at once (it waits only briefly for us); the volume is changed right after
        final int action = e.getAction(), repeats = e.getRepeatCount();
        h.post(() -> {
            try {
                touched();
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    if (repeats > 0) return;                         // repeats come from our own timer
                    stepVolume(k);
                    if (k != android.view.KeyEvent.KEYCODE_VOLUME_MUTE) {
                        heldKey = k; heldSteps = 0;
                        h.removeCallbacks(repeat);
                        h.postDelayed(repeat, 380);
                    }
                } else if (action == android.view.KeyEvent.ACTION_UP && k == heldKey) {
                    heldKey = 0; h.removeCallbacks(repeat);
                }
            } catch (Exception ex) { note("key: " + ex); }
        });
        return true;
    }

    private void stepVolume(final int key) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int dir = key == android.view.KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
            : key == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
        try {
            if (dir != AudioManager.ADJUST_TOGGLE_MUTE && am.isStreamMute(AudioManager.STREAM_MUSIC))
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);   // a step unmutes, as the TV does
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0);   // no FLAG_SHOW_UI: the TV's bar stays away
        } catch (Exception ignored) { }
        showVolume(am.getStreamVolume(AudioManager.STREAM_MUSIC), am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            am.isStreamMute(AudioManager.STREAM_MUSIC));
    }

    /** the wheel's window: made once, when the service starts, so a key never waits for it */
    private void ensureWheel() {
        if (volView != null) return;
        try {
            wm = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            volView = new VolumeWheel(this);
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                Math.round(dm.widthPixels * .062f), Math.round(dm.heightPixels * .40f),
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL;
            lp.x = Math.round(dm.widthPixels * .03f);
            wm.addView(volView, lp);
        } catch (Exception e) {
            volView = null;
            note("wheel: " + e);
        }
    }

    private void showVolume(int v, int max, boolean muted) {
        ensureWheel();
        if (volView != null) volView.show(v, max, muted);
    }

    private void hideVolume() {
        if (volView == null) return;
        try { wm.removeView(volView); } catch (Exception ignored) { }
        volView = null;
    }

    /** the last trouble, for the settings row (an error here must not cost the service its keys) */
    static volatile String lastError = "";
    // what the TV hands us, for the settings row: keys seen at all, volume keys among them, the key filter granted
    static volatile int keysSeen, volumeSeen;
    static volatile boolean filterOn;

    /** «أزرار ١٢ · صوت ٤ · الاعتراض يعمل»: whether the TV gives this service the keys at all */
    static String diagnose() {
        IdleWatch w = self;
        if (w == null) return "الخدمة لا تعمل";
        return "أزرار " + keysSeen + " · صوت " + volumeSeen + " · " + (filterOn ? "الاعتراض مفعّل" : "الاعتراض غير مسموح: أوقف الخدمة وشغّلها");
    }
    private static void note(String e) { lastError = e; }

    @Override
    protected void onServiceConnected() {
        self = this;
        try {
            applyVolumeSetting();
            if (new Prefs(this).volumeWheel()) ensureWheel();
        } catch (Exception e) { note("start: " + e); }
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
        if (self == this) self = null;
        hideVolume();
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
