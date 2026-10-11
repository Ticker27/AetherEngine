package com.aether.host.virtualization.components.service

/**
 * Keep-alive decision for [DaemonService], as pure data.
 *
 * `onTaskRemoved` fires when the user swipes the host task away. A virtualization host that wants
 * to survive that must restart itself — and the naive version of that is a restart storm: the
 * task is removed, the service restarts, the system removes it again, forever, draining battery
 * and eventually getting the app killed by the watchdog.
 *
 * The policy therefore bounds the restart: a maximum number of restarts inside a sliding window,
 * with a delay that doubles per attempt up to a ceiling. All of it is state the caller owns, so
 * the whole decision is unit-testable without a device.
 */
class DaemonRestartPolicy(
    private val maxRestarts: Int = DEFAULT_MAX_RESTARTS,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
) {
    /** One recorded task-removal event. */
    data class Event(val atMillis: Long)

    /** What the service should do next. */
    sealed interface Decision {
        /** Restart after [delayMillis]. */
        data class Restart(val delayMillis: Long) : Decision

        /** Do not restart; the budget inside the current window is spent. */
        data object Exhausted : Decision
    }

    private val events = ArrayDeque<Event>()

    /** Events still inside the window, oldest first. */
    fun recentEvents(nowMillis: Long): List<Event> {
        dropStale(nowMillis)
        return events.toList()
    }

    /** Restarts already spent inside the window at [nowMillis]. */
    fun restartsInWindow(nowMillis: Long): Int = recentEvents(nowMillis).size

    /** Remaining restarts inside the window at [nowMillis]. */
    fun remainingRestarts(nowMillis: Long): Int =
        (maxRestarts - restartsInWindow(nowMillis)).coerceAtLeast(0)

    /**
     * Records a task removal at [nowMillis] and returns the decision.
     * The event is recorded only when a restart is granted, so a rejected attempt does not
     * consume budget it was denied.
     */
    fun onTaskRemoved(nowMillis: Long): Decision {
        dropStale(nowMillis)
        if (events.size >= maxRestarts) return Decision.Exhausted
        events.addLast(Event(nowMillis))
        val attempt = events.size
        val delay = delayFor(attempt)
        return Decision.Restart(delay)
    }

    /** Exponential backoff: `base * 2^(attempt-1)`, clamped to [maxDelayMillis]. */
    fun delayFor(attempt: Int): Long {
        if (attempt <= 0) return 0L
        var delay = baseDelayMillis
        repeat(attempt - 1) {
            delay = if (delay >= maxDelayMillis) maxDelayMillis else delay * 2
        }
        return delay.coerceAtMost(maxDelayMillis)
    }

    /** Clears all recorded events; used by tests and by an explicit host restart. */
    fun reset() {
        events.clear()
    }

    private fun dropStale(nowMillis: Long) {
        val cutoff = nowMillis - windowMillis
        while (events.isNotEmpty() && events.first().atMillis < cutoff) {
            events.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_MAX_RESTARTS: Int = 5
        const val DEFAULT_WINDOW_MILLIS: Long = 60_000L
        const val DEFAULT_BASE_DELAY_MILLIS: Long = 1_000L
        const val DEFAULT_MAX_DELAY_MILLIS: Long = 30_000L
    }
}
