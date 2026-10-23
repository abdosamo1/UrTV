package sa.urtv.screensaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Plays one adhan from assets/adhan, over whatever is on:
 *  - asks for audio focus, so YouTube and other players pause, and carry on afterwards;
 *  - unmutes and sets the adhan volume (settings, 10 by default), and puts both back as they were afterwards;
 *  - a TV in standby is woken for it and put back to sleep afterwards (lockNow, needs the device admin that
 *    optimize/install.sh turns on); a TV that was on stays on.
 * A foreground service, so Android does not stop it half way.
 */
public class AdhanService extends Service implements AudioManager.OnAudioFocusChangeListener {
    private static final String CHANNEL = "adhan";
    private static final long MAX_MS = 8 * 60_000L;   // no adhan is longer; a stuck player is stopped

    private static AdhanService current;

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager am;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock cpu, screen;
    private int savedVolume = -1;
    private boolean savedMute, wasOn = true, done;

    static boolean playing() { return current != null; }

    /** voice: a file name from Adhan.VOICES; test: from settings or ADB (the TV is on anyway) */
    static void start(Context c, String voice, boolean test) {
        Intent i = new Intent(c, AdhanService.class).putExtra("voice", voice).putExtra("test", test);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void stop() {
        if (current != null) current.finish();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        foreground();
        if (current != null) return START_NOT_STICKY;   // one adhan at a time
        current = this;
        String voice = intent == null ? null : intent.getStringExtra("voice");
        if (voice == null || !Adhan.isVoice(voice)) voice = Adhan.DEFAULT_VOICE;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "urtv:adhan");
        cpu.acquire(MAX_MS + 60_000L);
        wasOn = pm.isInteractive();
        if (!wasOn) wake(pm);

        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            savedMute = Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC);
            if (savedMute) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, Math.min(max, new Prefs(this).adhanVolume())), 0);
        } catch (SecurityException ignored) {   // a TV that refuses: play at its current volume
        }
        requestFocus();

        try {
            AssetFileDescriptor fd = getAssets().openFd("adhan/" + voice + ".mp3");
            player = new MediaPlayer();
            player.setAudioAttributes(attributes());
            player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, what, extra) -> { finish(); return true; });
            player.prepare();
            // a TV just woken needs a moment before its speakers play
            h.postDelayed(() -> { if (!done && player != null) player.start(); }, wasOn ? 300 : 4000);
            h.postDelayed(this::finish, MAX_MS);
        } catch (Exception e) {
            finish();
        }
        return START_NOT_STICKY;
    }

    /** Volume and mute back as they were, the other app's sound back, and a TV that was off back to sleep. */
    void finish() {
        if (done) return;
        done = true;
        h.removeCallbacksAndMessages(null);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        if (am != null) {
            if (Build.VERSION.SDK_INT >= 26 && focus != null) am.abandonAudioFocusRequest(focus);
            else am.abandonAudioFocus(this);
            try {
                if (savedVolume >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
                if (savedMute) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            } catch (SecurityException ignored) { }
        }
        if (screen != null && screen.isHeld()) screen.release();
        if (!wasOn) sleep();
        if (cpu != null && cpu.isHeld()) cpu.release();
        current = null;
        stopForeground(true);
        stopSelf();
    }

    @SuppressWarnings("deprecation")   // the only way a background app turns the screen on; TURN_SCREEN_ON on 14
    private void wake(PowerManager pm) {
        screen = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
            | PowerManager.ON_AFTER_RELEASE, "urtv:adhan-wake");
        screen.acquire(MAX_MS + 60_000L);
    }

    private void sleep() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        try {
            if (dpm != null && dpm.isAdminActive(admin)) dpm.lockNow();
        } catch (SecurityException ignored) { }
    }

    private AudioAttributes attributes() {
        return new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    }

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        // transient: players pause (not stop) and resume when we let go
        if (Build.VERSION.SDK_INT >= 26) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(this, h).build();
            am.requestAudioFocus(focus);
        } else {
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) finish();   // the owner started something else on purpose
    }

    private void foreground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(R.drawable.ic_screensaver).setContentTitle("الأذان").setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
    }

    @Override
    public void onDestroy() {
        finish();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
