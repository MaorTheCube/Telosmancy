package me.telosmancy.features.impl.misc

import net.minecraft.network.chat.Component

object ItemLinkFeature {
    // Outgoing item share is handled by ItemShareModule.onOutgoingMessage via ClientPlayNetworkHandlerMixin
    fun processOutgoing(message: String): String = message
    fun transformSync(component: Component): Component = component
}
