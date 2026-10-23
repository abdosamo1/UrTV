package sa.urtv.screensaver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Some TVs (TCL) leave the app cold after a power cycle and the first dream fails.
 * Any boot broadcast that does arrive brings the process up and re-arms the update job;
 * the persisted job covers TVs that never send one.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        UpdateJob.schedule(c);
        // alarms do not survive a restart, and a changed clock moves the next adhan
        final PendingResult r = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            Adhan.refresh(app, false);
            Adhan.schedule(app);
            r.finish();
        }).start();
    }
}
