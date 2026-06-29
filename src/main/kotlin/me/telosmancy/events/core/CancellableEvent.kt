package me.telosmancy.events.core

import me.telosmancy.utils.logError

/**
 * Base class for events that can be cancelled.
 */
abstract class CancellableEvent : Event {
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }

    override fun postAndCatch(): Boolean {
        runCatching {
            EventBus.post(this)
        }.onFailure {
            logError(it, this)
        }
        return isCancelled
    }
}
