package me.telosmancy.features.impl.visual

import me.telosmancy.Telosmancy.mc
import me.telosmancy.clickgui.settings.Setting.Companion.withDependency
import me.telosmancy.clickgui.settings.impl.BooleanSetting
import me.telosmancy.clickgui.settings.impl.HUDSetting
import me.telosmancy.clickgui.settings.impl.SelectorSetting
import me.telosmancy.events.TickEvent
import me.telosmancy.events.core.on
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.features.impl.ClickGUIModule
import me.telosmancy.utils.ui.rendering.NVGPIPRenderer
import me.telosmancy.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.sqrt

object SpeedDisplayModule : Module(
    name = "Speed Display",
    category = Category.VISUAL,
    description = "Displays your movement speed in blocks per second."
) {

    private val mode by SelectorSetting(
        "Mode", "Horizontal", listOf("Horizontal", "Total"),
        desc = "Horizontal ignores vertical movement; Total includes it."
    )
    private val showLabel by BooleanSetting("Show Label", true, desc = "Append 'BPS' after the number.")

    private val hud by HUDSetting(
        name = "Speed",
        x = 10, y = 200, scale = 1f,
        toggleable = false,
        description = "Position of the speed display on the HUD.",
        module = this
    ) { example ->
        val sf = mc.window.guiScale.toFloat()
        val label = formatBps(if (example) 12.5f else smoothedBps)
        if (example) {
            // Preview text in the HUD editor only; gameplay uses renderHud() via NVG
            pose().pushMatrix()
            pose().scale(sf, sf)
            text(mc.font, label, 0, 0, 0xFFFFFFFF.toInt())
            pose().popMatrix()
        }
        mc.font.width(label).coerceAtLeast(40) to mc.font.lineHeight.coerceAtLeast(14)
    }

    private var smoothedBps = 0f
    private var lastX = 0.0
    private var lastZ = 0.0
    private var lastY = 0.0
    private var hasLastPos = false

    init {
        on<TickEvent.End> {
            val player = mc.player
            if (!enabled || player == null) {
                hasLastPos = false
                return@on
            }

            if (!hasLastPos) {
                lastX = player.x; lastY = player.y; lastZ = player.z
                hasLastPos = true
                return@on
            }

            val dx = player.x - lastX
            val dy = player.y - lastY
            val dz = player.z - lastZ
            lastX = player.x; lastY = player.y; lastZ = player.z

            val dist = if (mode == 0)
                sqrt(dx * dx + dz * dz)
            else
                sqrt(dx * dx + dy * dy + dz * dz)
            val bps = (dist * 20).toFloat()

            // Decay fast to 0 when stopped; blend smoothly otherwise
            smoothedBps = if (bps < 0.05f) smoothedBps * 0.5f else smoothedBps * 0.7f + bps * 0.3f
            if (smoothedBps < 0.05f) smoothedBps = 0f
        }
    }

    fun renderHud(context: GuiGraphicsExtractor) {
        if (!enabled) return
        mc.player ?: return

        val gs      = ClickGUIModule.getStandardGuiScale()
        val u       = mc.window.guiScale.toFloat() / gs * hud.scale
        val nx      = hud.screenX.toFloat() / gs
        val ny      = hud.screenY.toFloat() / gs
        val screenW = mc.window.screenWidth
        val screenH = mc.window.screenHeight

        NVGPIPRenderer.draw(context, 0, 0, screenW, screenH) {
            NVGRenderer.scale(gs, gs)
            NVGRenderer.textShadow(formatBps(smoothedBps), nx, ny, 9f * u, 0xFFFFFFFF.toInt(), NVGRenderer.defaultFont)
        }
    }

    private fun formatBps(bps: Float) = if (showLabel) "${"%.1f".format(bps)} BPS" else "%.1f".format(bps)
}
