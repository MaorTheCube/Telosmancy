package me.telosmancy.events

import me.telosmancy.Telosmancy
import me.telosmancy.utils.render.RenderBatchManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents

/**
 * Bridges Fabric lifecycle events into the telosmancy EventBus.
 */
object EventDispatcher {

    init {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            WorldLoadEvent().postAndCatch()
        }

        ClientTickEvents.START_LEVEL_TICK.register { world ->
            TickEvent.Start(world).postAndCatch()
        }

        ClientTickEvents.END_LEVEL_TICK.register { world ->
            TickEvent.End(world).postAndCatch()
        }

        LevelRenderEvents.END_EXTRACTION.register { handler ->
            Telosmancy.mc.level?.let { RenderEvent.Extract(handler, RenderBatchManager.renderConsumer).postAndCatch() }
        }

        LevelRenderEvents.END_MAIN.register { context ->
            Telosmancy.mc.level?.let { RenderEvent.Last(context).postAndCatch() }
        }
        
        // Note: ChatPacketEvent is fired by MessageHandlerMixin
        // Note: Chat message filtering is handled by MessageHandlerMixin
    }
}

