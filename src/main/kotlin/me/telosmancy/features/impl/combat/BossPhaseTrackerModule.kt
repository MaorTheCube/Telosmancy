package me.telosmancy.features.impl.combat

import me.telosmancy.Telosmancy
import me.telosmancy.clickgui.settings.Setting.Companion.withDependency
import me.telosmancy.clickgui.settings.impl.*
import me.telosmancy.events.BossBarUpdateEvent
import me.telosmancy.events.TickEvent
import me.telosmancy.events.core.on
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.utils.Color
import me.telosmancy.utils.createSoundSettings
import me.telosmancy.utils.emoji.EmojiReplacer
import me.telosmancy.utils.playSoundSettings
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.UUID

object BossPhaseTrackerModule : Module(
    name = "Boss Phase Tracker",
    category = Category.COMBAT,
    description = "Plays a sound and shows a title when a boss crosses key health thresholds."
) {

    // ── Threshold toggles ─────────────────────────────────────────────
    private val ping75    by BooleanSetting("Ping at 75%",    true,  desc = "Alert when boss health crosses 75%.")
    private val ping50    by BooleanSetting("Ping at 50%",    true,  desc = "Alert when boss health crosses 50%.")
    private val ping25    by BooleanSetting("Ping at 25%",    true,  desc = "Alert when boss health crosses 25%.")
    private val pingDeath by BooleanSetting("Ping at Death",  true,  desc = "Alert when the boss is defeated.")

    // ── Sound ─────────────────────────────────────────────────────────
    val playSound = registerSetting(BooleanSetting("Play Sound", true, desc = "Play a sound on each threshold."))
    private val soundSettings = createSoundSettings(
        name   = "Sound",
        default = "entity.wither.death",
        dependencies = { playSound.value }
    )

    // ── Title HUD ─────────────────────────────────────────────────────
    private val titleHud by HUDSetting(
        name        = "Title Display",
        x           = 400,
        y           = 160,
        scale       = 2f,
        toggleable  = true,
        default     = true,
        description = "Position of the boss phase title. Toggle to show/hide.",
        module      = this
    ) { example ->
        val title = if (example) {
            buildTitle("Boss at 50%!", Color(255, 140, 0).rgba)
        } else {
            currentTitle ?: return@HUDSetting 0 to 0
        }
        val tw = mc.font.width(title)
        val th = mc.font.lineHeight
        text(mc.font, title, 0, 0, Color(255, 255, 255).rgba, true)
        tw to th
    }

    private val duration by NumberSetting(
        "Duration", 60f, 10f, 200f,
        desc = "How many ticks the title stays on screen."
    ).withDependency { titleHud.enabled }

    // ── State ─────────────────────────────────────────────────────────
    private val trackedBars = HashMap<UUID, Float>()
    private var currentTitle: Component? = null
    private var titleTicks = 0

    init {
        on<BossBarUpdateEvent> {
            if (!enabled) return@on

            // Detect removed bars → boss death
            val removedIds = trackedBars.keys - bossBarMap.keys
            for (uuid in removedIds) {
                val lastProgress = trackedBars.remove(uuid) ?: continue
                if (pingDeath && lastProgress > 0.01f) {
                    trigger("BOSS DEAD!", Color(0, 230, 100).rgba)
                }
            }

            // Detect threshold crossings on active bars
            for ((uuid, event) in bossBarMap) {
                val current = event.progress
                val last    = trackedBars[uuid]

                if (last != null && current < last) {
                    // Check thresholds from lowest to highest so the most critical wins the title
                    if (pingDeath && last > 0.01f && current <= 0.01f)
                        trigger("BOSS DEAD!", Color(0, 230, 100).rgba)
                    else if (ping25 && last > 0.25f && current <= 0.25f)
                        trigger("Boss at 25%!", Color(220, 50, 50).rgba)
                    else if (ping50 && last > 0.50f && current <= 0.50f)
                        trigger("Boss at 50%!", Color(255, 140, 0).rgba)
                    else if (ping75 && last > 0.75f && current <= 0.75f)
                        trigger("Boss at 75%!", Color(255, 220, 0).rgba)
                }

                trackedBars[uuid] = current
            }
        }

        on<TickEvent.End> {
            if (titleTicks > 0) {
                titleTicks--
                if (titleTicks <= 0) currentTitle = null
            }
        }
    }

    private fun trigger(message: String, color: Int) {
        if (titleHud.enabled) {
            currentTitle = buildTitle(message, color)
            titleTicks = duration.toInt()
        }
        if (playSound.value) {
            try { playSoundSettings(soundSettings()) }
            catch (e: Exception) { Telosmancy.logger.warn("BossPhaseTracker: sound error: ${e.message}") }
        }
    }

    private fun buildTitle(text: String, color: Int): Component =
        EmojiReplacer.replaceIn(Component.literal(text).withStyle(Style.EMPTY.withColor(color)))

    override fun onDisable() {
        super.onDisable()
        trackedBars.clear()
        currentTitle = null
        titleTicks = 0
    }
}
