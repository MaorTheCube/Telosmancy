package me.telosmancy.events

import me.telosmancy.events.core.CancellableEvent as CoreCancellableEvent

/**
 * Base class for events that can be cancelled.
 * This is a type alias/wrapper for the core CancellableEvent class.
 */
typealias CancellableEvent = CoreCancellableEvent
