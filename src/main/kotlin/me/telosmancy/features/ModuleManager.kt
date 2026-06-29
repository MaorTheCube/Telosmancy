package me.telosmancy.features

import me.telosmancy.Telosmancy
import me.telosmancy.clickgui.settings.impl.HUDSetting
import me.telosmancy.clickgui.settings.impl.KeybindSetting
import me.telosmancy.config.ModuleConfig
import me.telosmancy.events.InputEvent
import me.telosmancy.events.core.on
import me.telosmancy.features.impl.combat.*
import me.telosmancy.features.impl.dungeon.ColorRoomSolverModule
import me.telosmancy.features.impl.misc.*
import me.telosmancy.features.impl.secret.*
import me.telosmancy.features.impl.tracking.*
import me.telosmancy.features.impl.tracking.bosstracker.TrackerModule
import me.telosmancy.features.impl.visual.*
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.io.File

/**
 * Handles module registration, loading, and saving.
 */
object ModuleManager {

    /**
     * Map containing all modules in Telosmancy,
     * where the key is the modules name in lowercase.
     */
    val modules: HashMap<String, Module> = hashMapOf()

    /**
     * Map containing all modules under their category.
     */
    val modulesByCategory: HashMap<Category, ArrayList<Module>> = hashMapOf()

    /**
     * List of all configurations handled by Telosmancy.
     */
    val configs: ArrayList<ModuleConfig> = arrayListOf()

    val keybindSettingsCache: ArrayList<KeybindSetting> = arrayListOf()
    val hudSettingsCache: ArrayList<HUDSetting> = arrayListOf()

    private val HUD_LAYER: Identifier = Identifier.fromNamespaceAndPath(Telosmancy.MOD_ID, "telosmancy_hud")

    /**
     * Registers modules to the [ModuleManager] and initializes them.
     *
     * @param config the config the [Module] is saved to,
     * it is recommended that each unique mod that uses this has its own config
     */
    fun registerModules(config: ModuleConfig, vararg modules: Module) {
        for (module in modules) {
            if (module.isDevModule && !FabricLoader.getInstance().isDevelopmentEnvironment) continue

            val lowercase = module.name.lowercase()
            config.modules[lowercase] = module
            this.modules[lowercase] = module
            this.modulesByCategory.getOrPut(module.category) { arrayListOf() }.add(module)

            // Subscribe module to EventBus so its event listeners work
            me.telosmancy.events.core.EventBus.subscribe(module)

            module.key?.let { keybind ->
                val setting = KeybindSetting("Keybind", keybind, "Toggles this module.")
                setting.onPress = module::onKeybind
                module.registerSetting(setting)
            }

            for ((_, setting) in module.settings) {
                when (setting) {
                    is KeybindSetting -> keybindSettingsCache.add(setting)
                    is HUDSetting -> hudSettingsCache.add(setting)
                }
            }
        }
        configs.add(config)
        config.load()
    }

    /**
     * Loads all configs from disk.
     */
    fun loadConfigurations() {
        for (config in configs) {
            config.load()
        }
    }

    /**
     * Saves all configs to disk.
     */
    fun saveConfigurations() {
        for (config in configs) {
            config.save()
        }
    }

    fun render(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        val mc = Telosmancy.mc
        if (mc.level == null || mc.player == null || mc.screen == me.telosmancy.clickgui.HudManager || mc.options.hideGui) return
        
        AbilityCooldownModule.renderHud(context)
        ModuleListModule.renderHud(context)
        PlayerHPBarModule.renderHud(context)
        SpeedDisplayModule.renderHud(context)

        context.pose().pushMatrix()
        val sf = mc.window.guiScale
        context.pose().scale(1f / sf, 1f / sf)
        for (hudSettings in hudSettingsCache) {
            if (hudSettings.isEnabled) hudSettings.value.draw(context, false)
        }
        context.pose().popMatrix()
    }

    /**
     * Get a module by name.
     */
    fun getModule(name: String): Module? = modules[name.lowercase()]

    /**
     * Get all modules in a category.
     */
    fun getModulesByCategory(category: Category): List<Module> =
        modulesByCategory[category] ?: emptyList()

    /**
     * Enable a module by name.
     */
    fun enableModule(name: String) {
        getModule(name)?.let { if (!it.enabled) it.toggle() }
    }

    /**
     * Disable a module by name.
     */
    fun disableModule(name: String) {
        getModule(name)?.let { if (it.enabled) it.toggle() }
    }

    /**
     * Toggle a module by name.
     */
    fun toggleModule(name: String) {
        getModule(name)?.toggle()
    }

    init {
        // Register all modules
        registerModules(
            config = ModuleConfig(file = File(Telosmancy.configFile, "telosmancy-config.json")),
            // Combat
            AutoClickerModule,
            WeaponRangeModule,
            AbilityRangeModule,
            ArmorCooldownsModule,
            AbilityCooldownModule,

            // Visual
            FullbrightModule,
            ModuleListModule,
            PlayerSizeModule,
            CameraModule,
            HitboxModule,
            HideArmorModule,
            ArmorHUDModule,
            PlayerHPBarModule,
            SpeedDisplayModule,
            
            // Tracking
            LifetimeStatsModule,
            PityCounterModule,
            TrackerModule,
            SessionManagerModule,

            // Secret
            SpeedBoostModule,
            HClipModule,
            FOVChangerModule,
            GUIMoveModule,

            // Dungeon
            ColorRoomSolverModule,

            // Misc
            KeybindsModule,
            HideHeldTooltipsModule,
            ChatModule,
            // Utility
            me.telosmancy.features.impl.combat.AutoSprintModule,
            me.telosmancy.features.impl.ClickGUIModule
        )

        // Register input event handler for keybinds
        on<InputEvent> {
            for (setting in keybindSettingsCache) {
                if (setting.value == key) {
                    setting.onPress?.invoke()
                }
            }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, HUD_LAYER, ModuleManager::render)
    }
}