package com.example.smsprinter;
import android.app.job.JobParameters;
import android.app.job.JobService;
public final class RetryService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        Work.executor.execute(() -> { try (Queue q = new Queue(getApplicationContext())) { q.drain(getApplicationContext()); } finally { jobFinished(params, false); } });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
