package dev.kopandazavr.codexmonitor;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/* JADX INFO: loaded from: classes.dex */
public final class UsageRefreshJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentMap<Integer, JobRun> active = new ConcurrentHashMap();

    @Override // android.app.job.JobService
    public boolean onStartJob(final JobParameters jobParameters) {
        if (!hasAnySignedInAccount(this)) {
            DiagnosticLog.info(this, "scheduler", "refresh_job_skipped_signed_out",
                    "job_id", jobParameters.getJobId());
            WidgetRenderer.updateAll(this);
            return false;
        }
        final JobRun jobRun = new JobRun(jobParameters);
        FutureTask<Void> futureTask = new FutureTask<Void>(jobRun, null) {
            @Override // java.util.concurrent.FutureTask
            protected void done() {
                if (isCancelled()) {
                    UsageRefreshJobService.this.active.remove(
                            Integer.valueOf(jobParameters.getJobId()), jobRun);
                }
            }
        };
        jobRun.task = futureTask;
        JobRun jobRunPut = this.active.put(Integer.valueOf(jobParameters.getJobId()), jobRun);
        if (jobRunPut != null) {
            jobRunPut.stopped = true;
            jobRunPut.task.cancel(true);
        }
        this.executor.execute(futureTask);
        return true;
    }

    @Override // android.app.job.JobService
    public boolean onStopJob(JobParameters jobParameters) {
        DiagnosticLog.warn(this, "scheduler", "refresh_job_stopped",
                "job_id", jobParameters.getJobId());
        JobRun jobRunRemove = this.active.remove(Integer.valueOf(jobParameters.getJobId()));
        if (jobRunRemove != null) {
            jobRunRemove.stopped = true;
            jobRunRemove.task.cancel(true);
        }
        return true;
    }

    @Override // android.app.Service
    public void onDestroy() {
        for (JobRun jobRun : this.active.values()) {
            jobRun.stopped = true;
            jobRun.task.cancel(true);
        }
        this.active.clear();
        this.executor.shutdownNow();
        super.onDestroy();
    }

    private final class JobRun implements Runnable {
        private final JobParameters params;
        private final boolean chainedCycle;
        private final String reason;
        private volatile boolean stopped;
        private FutureTask<Void> task;

        JobRun(JobParameters jobParameters) {
            this.params = jobParameters;
            this.reason = jobParameters.getExtras() == null
                    ? "" : jobParameters.getExtras().getString("reason", "");
            this.chainedCycle = RefreshScheduler.REASON_SHORT_PERIODIC.equals(this.reason)
                    || RefreshScheduler.REASON_ADAPTIVE.equals(this.reason);
        }

        @Override // java.lang.Runnable
        public void run() {
            android.content.Context app =
                    UsageRefreshJobService.this.getApplicationContext();
            long started = android.os.SystemClock.elapsedRealtime();
            boolean forceSubscription = "immediate".equals(this.reason);
            int attempted = 0;
            int succeeded = 0;
            String selectedId = AccountContainerStore.selectedId(app);
            DiagnosticLog.info(UsageRefreshJobService.this, "scheduler",
                    "refresh_job_started",
                    "job_id", this.params.getJobId(),
                    "reason", this.reason,
                    "force_subscription", forceSubscription,
                    "account_count", AccountContainerStore.all(app).size());
            try {
                for (AccountContainerStore.Account account : AccountContainerStore.all(app)) {
                    if (this.stopped || Thread.currentThread().isInterrupted()) break;
                    if (!SecureTokenStore.isSignedIn(app, account.id)) continue;
                    attempted++;
                    long accountStarted = android.os.SystemClock.elapsedRealtime();
                    try {
                        UsageSnapshot snapshot = UsageApi.refreshAndCacheScheduled(
                                app, account.id, forceSubscription, this.reason);
                        AppPreferences.recordRefreshSuccess(app, account.id);
                        succeeded++;
                        if (account.id.equals(selectedId)) {
                            ContextStartMonitor.startIfRequested(app);
                        }
                        DiagnosticLog.info(UsageRefreshJobService.this, "scheduler",
                                "refresh_account_succeeded",
                                "container_id", account.id,
                                "reason", this.reason,
                                "duration_ms",
                                android.os.SystemClock.elapsedRealtime() - accountStarted);
                    } catch (Exception accountError) {
                        AppPreferences.setLastError(app, account.id, safeMessage(accountError));
                        AppPreferences.recordRefreshFailure(app, account.id);
                        DiagnosticLog.error(UsageRefreshJobService.this, "scheduler",
                                "refresh_account_failed", accountError,
                                "container_id", account.id,
                                "reason", this.reason,
                                "duration_ms",
                                android.os.SystemClock.elapsedRealtime() - accountStarted);
                    }
                }

                RefreshScheduler.scheduleAtNextKnownReset(app);
                WidgetRenderer.updateAll(app);
                boolean retry = attempted > 0 && succeeded == 0 && !this.chainedCycle;
                DiagnosticLog.info(UsageRefreshJobService.this, "scheduler",
                        "refresh_job_finished",
                        "job_id", this.params.getJobId(),
                        "reason", this.reason,
                        "accounts_attempted", attempted,
                        "accounts_succeeded", succeeded,
                        "duration_ms", android.os.SystemClock.elapsedRealtime() - started);
                UsageRefreshJobService.this.active.remove(
                        Integer.valueOf(this.params.getJobId()), this);
                if (!this.stopped) {
                    UsageRefreshJobService.this.jobFinished(this.params, retry);
                    if (this.chainedCycle && hasAnySignedInAccount(app)) {
                        RefreshScheduler.scheduleNextShort(app, this.params.getJobId());
                    }
                }
            } catch (Throwable failure) {
                WidgetRenderer.updateAll(app);
                UsageRefreshJobService.this.active.remove(
                        Integer.valueOf(this.params.getJobId()), this);
                if (!this.stopped) {
                    UsageRefreshJobService.this.jobFinished(this.params, false);
                    if (this.chainedCycle && hasAnySignedInAccount(app)) {
                        RefreshScheduler.scheduleNextShort(app, this.params.getJobId());
                    }
                }
                throw failure;
            }
        }
    }

    private static boolean hasAnySignedInAccount(android.content.Context context) {
        for (AccountContainerStore.Account account : AccountContainerStore.all(context)) {
            if (SecureTokenStore.isSignedIn(context, account.id)) return true;
        }
        return false;
    }

    private static final class ContextStartMonitor {
        private ContextStartMonitor() {
        }

        static void startIfRequested(android.content.Context context) {
            if (!QuickSetupPreferences.shouldStartMonitor(context)) return;
            if (NowBarManager.start(context)) {
                QuickSetupPreferences.clearMonitorStart(context);
                DualUsageNotificationManager.repostDelayed(context, 200L);
                DiagnosticLog.info(context, "quick_setup", "live_monitor_started_after_refresh");
            }
        }
    }

    public static String safeMessage(Exception exc) {
        String message = exc.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Usage refresh failed.";
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
