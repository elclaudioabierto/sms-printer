package com.example.smsprinter;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Work {
    static final ExecutorService executor = Executors.newSingleThreadExecutor();
    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(new JobInfo.Builder(7319, new ComponentName(context, RetryService.class)).setPersisted(true).setPeriodic(15 * 60 * 1000L).build());
    }
    static void drain(Context context) { executor.execute(() -> { try (Queue q = new Queue(context.getApplicationContext())) { q.drain(context.getApplicationContext()); } }); }
}
