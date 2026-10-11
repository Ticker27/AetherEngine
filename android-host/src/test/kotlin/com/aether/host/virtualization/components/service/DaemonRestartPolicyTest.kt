package com.aether.host.virtualization.components.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keep-alive budget checks. The failure mode being guarded against is a restart storm, so the
 * tests assert that the budget is finite, that it slides, and that denied attempts cost nothing.
 */
class DaemonRestartPolicyTest {

    @Test
    fun firstRemovalRestartsWithBaseDelay() {
        val policy = DaemonRestartPolicy(maxRestarts = 5, windowMillis = 60_000, baseDelayMillis = 1_000)
        val decision = policy.onTaskRemoved(nowMillis = 1_000)
        assertTrue(decision is DaemonRestartPolicy.Decision.Restart)
        assertEquals(1_000L, (decision as DaemonRestartPolicy.Decision.Restart).delayMillis)
    }

    @Test
    fun delayDoublesPerAttemptUpToTheCeiling() {
        val policy = DaemonRestartPolicy(
            maxRestarts = 10,
            windowMillis = 600_000,
            baseDelayMillis = 1_000,
            maxDelayMillis = 8_000,
        )
        val delays = (1..6).map { policy.delayFor(it) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L, 8_000L), delays)
    }

    @Test
    fun budgetIsExhaustedAfterMaxRestarts() {
        val policy = DaemonRestartPolicy(maxRestarts = 3, windowMillis = 60_000)
        var now = 0L
        repeat(3) {
            val decision = policy.onTaskRemoved(now)
            assertTrue("attempt ${it + 1} should still be granted", decision is DaemonRestartPolicy.Decision.Restart)
            now += 1
        }
        val denied = policy.onTaskRemoved(now)
        assertEquals(DaemonRestartPolicy.Decision.Exhausted, denied)
        assertEquals(0, policy.remainingRestarts(now))
    }

    @Test
    fun deniedAttemptDoesNotConsumeBudget() {
        val policy = DaemonRestartPolicy(maxRestarts = 1, windowMillis = 60_000)
        policy.onTaskRemoved(nowMillis = 0)
        repeat(5) { assertEquals(DaemonRestartPolicy.Decision.Exhausted, policy.onTaskRemoved(nowMillis = 1)) }
        assertEquals(1, policy.restartsInWindow(nowMillis = 1))
    }

    @Test
    fun windowSlidesSoOldRemovalsStopCounting() {
        val policy = DaemonRestartPolicy(maxRestarts = 2, windowMillis = 10_000, baseDelayMillis = 1_000)
        policy.onTaskRemoved(nowMillis = 0)
        policy.onTaskRemoved(nowMillis = 5_000)
        assertEquals(2, policy.restartsInWindow(nowMillis = 9_999))
        assertEquals(DaemonRestartPolicy.Decision.Exhausted, policy.onTaskRemoved(nowMillis = 9_999))

        // After the window passes, the oldest event drops out and budget returns.
        assertEquals(0, policy.restartsInWindow(nowMillis = 20_000))
        val decision = policy.onTaskRemoved(nowMillis = 20_000)
        assertTrue(decision is DaemonRestartPolicy.Decision.Restart)
    }

    @Test
    fun resetClearsTheWindow() {
        val policy = DaemonRestartPolicy(maxRestarts = 1, windowMillis = 60_000)
        policy.onTaskRemoved(nowMillis = 0)
        assertEquals(DaemonRestartPolicy.Decision.Exhausted, policy.onTaskRemoved(nowMillis = 0))
        policy.reset()
        assertTrue(policy.onTaskRemoved(nowMillis = 0) is DaemonRestartPolicy.Decision.Restart)
    }

    @Test
    fun defaultConstantsAreSane() {
        assertTrue(DaemonRestartPolicy.DEFAULT_MAX_RESTARTS in 1..10)
        assertTrue(DaemonRestartPolicy.DEFAULT_BASE_DELAY_MILLIS < DaemonRestartPolicy.DEFAULT_MAX_DELAY_MILLIS)
        assertTrue(DaemonRestartPolicy.DEFAULT_WINDOW_MILLIS >= DaemonRestartPolicy.DEFAULT_MAX_DELAY_MILLIS)
        assertFalse(DaemonRestartPolicy.DEFAULT_MAX_RESTARTS <= 0)
    }
}
