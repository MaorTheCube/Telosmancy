package me.telosmancy

import me.telosmancy.commands.*
import me.telosmancy.events.EventDispatcher
import me.telosmancy.events.core.EventBus
import me.telosmancy.network.TelosDataFetcher
import me.telosmancy.utils.data.TelosData
import me.telosmancy.features.ModuleManager
import me.telosmancy.utils.IrisCompat
import me.telosmancy.utils.LocalAPI
import me.telosmancy.utils.ServerUtils
import me.telosmancy.utils.data.persistence.DataConfig
import me.telosmancy.utils.handlers.BossDefeatHandler
import me.telosmancy.utils.handlers.TickTasks
import me.telosmancy.utils.render.ItemStateRenderer
import me.telosmancy.utils.render.RenderBatchManager
import me.telosmancy.utils.ui.rendering.NVGPIPRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.Version
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File

/**
 * Main entry point for Telosmancy.
 */
object Telosmancy : ClientModInitializer {

    val logger: Logger = LogManager.getLogger("Telosmancy")

    @JvmStatic
    val mc: Minecraft = Minecraft.getInstance()

    /**
     * Main config file location.
     * @see me.telosmancy.config.ModuleConfig
     */
    val configFile: File = File(mc.gameDirectory, "config/telosmancy/").apply {
        try {
            if (isFile) delete() // Delete old bugged files that prevent creating the directory
            if (!exists()) mkdirs()
        } catch (e: Exception) {
            println("Error initializing module config\n${e.message}")
            logger.error("Error initializing module config", e)
        }
    }

    const val MOD_ID = "telosmancy"

    val version: Version by lazy { FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().metadata.version }

    override fun onInitializeClient() {
        // Initialize tracking data integration FIRST (before EventBus subscriptions)
        DataConfig.initialize()

        // Load Telos data (items/bosses/dungeons/portals), then update it from Git
        TelosData.init()
        TelosDataFetcher.fetchAll()

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            arrayOf(
                mainCommand,
                devCommand,
                acCommand,
                gcCommand,
                grcCommand,
                secretCommand,
                panicCommand
            ).forEach { commodore -> commodore.register(dispatcher) }
        }

        listOf(
            this, TickTasks, ServerUtils, EventDispatcher,
            BossDefeatHandler, RenderBatchManager, LocalAPI,
            IrisCompat, ModuleManager
        ).forEach { EventBus.subscribe(it) }

        PictureInPictureRendererRegistry.register { context ->
            NVGPIPRenderer(context.bufferSource())
        }

        PictureInPictureRendererRegistry.register { context ->
            ItemStateRenderer(context.bufferSource())
        }

        // Save on clean shutdown and on disconnect (covers force-close between sessions)
        ClientLifecycleEvents.CLIENT_STOPPING.register { shutdown() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ModuleManager.saveConfigurations() }

        // Initialize LocalAPI AFTER subscribing to EventBus
        LocalAPI.initialize()
    }

    /**
     * Shutdown Telosmancy.
     */
    fun shutdown() {
        logger.info("Shutting down Telosmancy...")
        
        try {
            // Save module configurations
            ModuleManager.saveConfigurations()
            
            // Shutdown DataConfig (handles async saves and creates final backup)
            DataConfig.shutdown()
            
            logger.info("Telosmancy shutdown complete")
        } catch (e: Exception) {
            logger.error("Error during Telosmancy shutdown: ${e.message}", e)
        }
    }
    

}