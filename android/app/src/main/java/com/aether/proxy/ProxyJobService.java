package com.aether.proxy;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * ProxyJobService — stub job service ของ container (P0–P3 ต่อ process)
 * โครงเดียวกับแบบจำลอง: JobScheduler proxy ต่อ process
 */
public class ProxyJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        android.util.Log.i("AetherEngine", "ProxyJobService.onStartJob " + getLocalClassName()
                + " job=" + params.getJobId());
        return false; // งานเสร็จทันทีใน structural model
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    public static class P0 extends ProxyJobService {}
    public static class P1 extends ProxyJobService {}
    public static class P2 extends ProxyJobService {}
    public static class P3 extends ProxyJobService {}
}
