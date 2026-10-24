package sa.urtv.screensaver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** PackageInstaller's answer to SelfUpdate's install (not exported: only the system's PendingIntent reaches it). */
public class UpdateReceiver extends BroadcastReceiver {
    static final String RESULT = "sa.urtv.screensaver.UPDATE_RESULT";

    @Override
    public void onReceive(Context c, Intent i) {
        if (RESULT.equals(i.getAction())) SelfUpdate.result(c, i);
    }
}
