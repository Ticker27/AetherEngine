package com.aether.host.virtualization.components.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind

/** Fixed slot pool for host-scheduled guest jobs; jobs are not persisted or rescheduled here. */
abstract class ProxyJobService : JobService() {
    protected abstract val slotIndex: Int

    override fun onStartJob(params: JobParameters): Boolean {
        dispatch("started", params)
        // No guest job adapter is installed yet, so Android work is complete immediately.
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        dispatch("stopped", params)
        return false
    }

    private fun dispatch(event: String, params: JobParameters) {
        (application as? AetherApplication)?.hostInitializer?.dispatch(
            HostComponentEvent(
                owner = this,
                kind = HostComponentKind.JOB_SERVICE,
                slot = slotIndex,
                lifecycle = event,
                proxyType = "job-p$slotIndex",
                jobId = params.jobId,
            ),
        )
    }
}

class ProxyJobServiceP0 : ProxyJobService() {
    override val slotIndex = 0
}

class ProxyJobServiceP1 : ProxyJobService() {
    override val slotIndex = 1
}

class ProxyJobServiceP2 : ProxyJobService() {
    override val slotIndex = 2
}

class ProxyJobServiceP3 : ProxyJobService() {
    override val slotIndex = 3
}
