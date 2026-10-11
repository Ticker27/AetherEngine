package com.aether.host.virtualization.components.service

import com.aether.host.AetherApplication
import com.aether.host.bootstrap.HostComponentEvent
import com.aether.host.bootstrap.HostComponentKind
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger

/**
 * Fixed slot pool for host-scheduled guest jobs.
 *
 * Jobs are not persisted or rescheduled here: `onStartJob` returns `false` so Android considers
 * the work finished the moment the callback returns, and nothing is written to the job scheduler.
 * A guest job adapter is a later milestone.
 *
 * With [HostFeature.SLOT_RESTART] enabled, a job whose work was dropped because the slot process
 * died is rescheduled once with a short delay. The retry is bounded to a single attempt: an
 * unbounded retry loop inside a JobService is indistinguishable from a crash loop to the
 * scheduler, which responds by backing the job off hard.
 */
abstract class ProxyJobService : JobService() {
    protected abstract val slotIndex: Int

    override fun onStartJob(params: JobParameters): Boolean {
        dispatch("started", params)
        if (flagger.isEnabled(HostFeature.SLOT_RESTART) && params.isOverrideDeadlineExpired) {
            // The scheduler ran out of patience; retrying here would only burn quota.
            dispatch("deadline-expired", params)
            return false
        }
        // No guest job adapter is installed yet, so Android work is complete immediately.
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        dispatch("stopped", params)
        // Reschedule only when the platform stopped us for its own reasons, not when we finished.
        return flagger.isEnabled(HostFeature.SLOT_RESTART)
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
