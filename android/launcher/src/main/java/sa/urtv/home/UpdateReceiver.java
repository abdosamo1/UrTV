package sa.urtv.home;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * The home screen app's self-update (SelfUpdate): PackageInstaller's answer (RESULT), and «look now» (UPDATE),
 * sent by the screensaver's settings («حدّث الآن») and its 6-hour job. UPDATE only makes it check the release,
 * so it may come from anywhere. While the home screen is in front the update waits: installing restarts it.
 */
public class UpdateReceiver extends BroadcastReceiver {
    static final String RESULT = "sa.urtv.home.UPDATE_RESULT";
    static final String UPDATE = "sa.urtv.home.UPDATE";

    @Override
    public void onReceive(Context c, Intent i) {
        if (RESULT.equals(i.getAction())) { SelfUpdate.result(c, i); return; }
        if (!UPDATE.equals(i.getAction())) return;
        final PendingResult r = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            SelfUpdate.check(app, "urtv-home.apk", false, MainActivity.inFront);
            r.finish();
        }).start();
    }

    /** Its own check every 6 hours, also when the screensaver app is missing. Persisted across restarts. */
    public static class Job extends JobService {
        private static final int ID = 1;

        static void schedule(Context c) {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == ID) return;
            js.schedule(new JobInfo.Builder(ID, new ComponentName(c, Job.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6L * 3600 * 1000)
                .setPersisted(true)
                .build());
        }

        @Override
        public boolean onStartJob(final JobParameters p) {
            new Thread(() -> {
                SelfUpdate.check(getApplicationContext(), "urtv-home.apk", false, MainActivity.inFront);
                jobFinished(p, false);
            }).start();
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters p) { return true; }
    }
}
