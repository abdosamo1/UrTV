package sa.urtv.screensaver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The adhan alarm (Adhan.schedule): play it if it is still its time, then arm the next one. */
public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Prefs p = new Prefs(c);
        int prayer = i.getIntExtra("prayer", -1);
        long at = i.getLongExtra("at", 0);
        if (p.adhanOn() && prayer >= 0 && AdhanTimes.onTime(at, System.currentTimeMillis())) {
            String voice = prayer == 0 ? p.adhanFajr() : p.adhanVoice();
            if (!voice.isEmpty()) AdhanService.start(c, voice, false);
        }
        final PendingResult r = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            Adhan.refresh(app, false);
            Adhan.schedule(app);
            r.finish();
        }).start();
    }
}
