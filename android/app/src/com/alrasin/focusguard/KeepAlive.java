package com.alrasin.focusguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import java.io.File;

/**
 * Non-root keep-alive. A periodic job (and boot) checks that the accessibility service is
 * really running: if it is off, or on but its heartbeat file has gone stale (killed by the
 * system, MIUI cleaner, ...), it posts "tap to fix". Android doesn't let an app turn its own
 * accessibility service back on, so the user's tap is the repair. Rooted phones have the
 * module's watchdog for this and skip the notice.
 */
public class KeepAlive extends JobService {
    private static final int JOB = 1, NOTE = 2;
    private static final long STALE_MS = 3 * 60_000L;

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js.getPendingJob(JOB) != null) return;
        js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, KeepAlive.class))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        check(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    static void check(Context c) {
        Strict.applyDue(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (Setup.rooted(c)) {
            nm.cancel(NOTE);
            return;
        }
        boolean on = Setup.accessibilityOn(c);
        File beat = new File(c.getFilesDir(), "heartbeat");
        boolean screenOn = c.getSystemService(PowerManager.class).isInteractive();
        boolean stale = !beat.exists() || System.currentTimeMillis() - beat.lastModified() > STALE_MS;
        // A screen-off service still beats; only judge staleness while the screen is on.
        boolean broken = !on || (screenOn && stale && GuardService.instance == null
                && android.os.SystemClock.elapsedRealtime() > STALE_MS); // not right after boot
        if (!broken) {
            nm.cancel(NOTE);
            return;
        }
        nm.createNotificationChannel(new NotificationChannel(
                "alerts", "Protection stopped", NotificationManager.IMPORTANCE_HIGH));
        Intent fix = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 0, fix,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(NOTE, new Notification.Builder(c, "alerts")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(on ? "Focus Guard stopped" : "Focus Guard is off")
                .setContentText(on ? "Android stopped it. Tap to fix." : "Tap to turn it back on.")
                .setContentIntent(pi)
                .setOngoing(true)
                .build());
    }

    static void cancel(Context c) {
        c.getSystemService(NotificationManager.class).cancel(NOTE);
    }

    /** Boot and app update: re-arm the job and check once. */
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            schedule(c);
            check(c);
        }
    }
}
