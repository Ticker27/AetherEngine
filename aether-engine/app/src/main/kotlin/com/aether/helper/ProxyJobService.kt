package com.aether.helper

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

open class ProxyJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i("AetherProxyJob", "onStartJob")
        return false
    }
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.i("AetherProxyJob", "onStopJob")
        return false
    }

    class P0 : ProxyJobService()
    class P1 : ProxyJobService()
    class P2 : ProxyJobService()
    class P3 : ProxyJobService()
}
