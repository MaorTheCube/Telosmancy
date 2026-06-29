package me.telosmancy.events

import me.telosmancy.events.core.Event
import net.minecraft.network.chat.Component

/**
 * Event fired when a chat message is received.
 */
class ChatPacketEvent(val value: String, val component: Component) : Event
