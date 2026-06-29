package me.telosmancy.clickgui

import me.telosmancy.Telosmancy
import me.telosmancy.features.ModuleManager
import me.telosmancy.features.ModuleManager.hudSettingsCache
import me.telosmancy.clickgui.settings.impl.HudElement
import me.telosmancy.utils.Colors
import me.telosmancy.utils.ui.mouseX as telosmancyMouseX
import me.telosmancy.utils.ui.mouseY as telosmancyMouseY
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

object HudManager : Screen(Component.literal("HUD Manager")) {

    private var dragging: HudElement? = null

    private var deltaX = 0f
    private var deltaY = 0f

    // Guide lines to draw this frame (populated during drag, consumed in render)
    private val xGuides = mutableListOf<Float>()
    private val yGuides = mutableListOf<Float>()

    override fun init() {
        for (hud in hudSettingsCache) {
            if (hud.isEnabled) {
                val sw = Telosmancy.mc.window.screenWidth
                val sh = Telosmancy.mc.window.screenHeight
                clampElement(hud.value, sw, sh)
            }
        }
        super.init()
    }

    private fun isShiftPressed(): Boolean {
        val windowHandle = Telosmancy.mc.window.handle()
        return GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }

    private fun clampElement(el: HudElement, sw: Int, sh: Int) {
        val w = (el.width * el.scale).toInt().coerceAtLeast(1)
        val h = (el.height * el.scale).toInt().coerceAtLeast(1)
        el.setScreenX(el.screenX.coerceIn(0, (sw - w).coerceAtLeast(0)))
        el.setScreenY(el.screenY.coerceIn(0, (sh - h).coerceAtLeast(0)))
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks)

        val sw = Telosmancy.mc.window.screenWidth
        val sh = Telosmancy.mc.window.screenHeight

        xGuides.clear()
        yGuides.clear()

        dragging?.let { el ->
            val actualWidth  = el.width  * el.scale
            val actualHeight = el.height * el.scale

            var newScreenX = telosmancyMouseX + deltaX
            var newScreenY = telosmancyMouseY + deltaY

            val elemX2 = newScreenX + actualWidth
            val elemCX = newScreenX + actualWidth  / 2f
            val elemY2 = newScreenY + actualHeight
            val elemCY = newScreenY + actualHeight / 2f

            // ── Element-to-element alignment snapping ────────────────────
            val elemSnapThreshold = 10f
            var bestXDist = elemSnapThreshold; var bestXSnap: Float? = null; var bestXGuide = 0f
            var bestYDist = elemSnapThreshold; var bestYSnap: Float? = null; var bestYGuide = 0f

            for (other in hudSettingsCache) {
                if (!other.isEnabled || other.value === el) continue
                val ow  = other.value.width  * other.value.scale
                val oh  = other.value.height * other.value.scale
                val ox1 = other.value.screenX.toFloat()
                val ox2 = ox1 + ow
                val ocx = ox1 + ow / 2f
                val oy1 = other.value.screenY.toFloat()
                val oy2 = oy1 + oh
                val ocy = oy1 + oh / 2f

                for (ref in listOf(ox1, ox2, ocx)) {
                    // Our left edge → their reference
                    val dL = abs(newScreenX - ref)
                    if (dL < bestXDist) { bestXDist = dL; bestXSnap = ref; bestXGuide = ref }
                    // Our center → their reference
                    val dC = abs(elemCX - ref)
                    if (dC < bestXDist) { bestXDist = dC; bestXSnap = ref - actualWidth / 2f; bestXGuide = ref }
                    // Our right edge → their reference
                    val dR = abs(elemX2 - ref)
                    if (dR < bestXDist) { bestXDist = dR; bestXSnap = ref - actualWidth; bestXGuide = ref }
                }
                for (ref in listOf(oy1, oy2, ocy)) {
                    val dT = abs(newScreenY - ref)
                    if (dT < bestYDist) { bestYDist = dT; bestYSnap = ref; bestYGuide = ref }
                    val dM = abs(elemCY - ref)
                    if (dM < bestYDist) { bestYDist = dM; bestYSnap = ref - actualHeight / 2f; bestYGuide = ref }
                    val dB = abs(elemY2 - ref)
                    if (dB < bestYDist) { bestYDist = dB; bestYSnap = ref - actualHeight; bestYGuide = ref }
                }
            }

            if (bestXSnap != null) { newScreenX = bestXSnap; xGuides.add(bestXGuide) }
            if (bestYSnap != null) { newScreenY = bestYSnap; yGuides.add(bestYGuide) }

            // ── Shift: snap to screen-center axes (recompute after element snap) ──
            if (isShiftPressed()) {
                val centerX       = sw / 2f
                val centerY       = sh / 2f
                val shiftThreshold = 15f
                val snapCX = newScreenX + actualWidth  / 2f
                val snapCY = newScreenY + actualHeight / 2f
                if (abs(snapCX - centerX) < shiftThreshold) { newScreenX = centerX - actualWidth  / 2f; xGuides.add(centerX) }
                if (abs(snapCY - centerY) < shiftThreshold) { newScreenY = centerY - actualHeight / 2f; yGuides.add(centerY) }
            }

            // ── Grid snap (50 px) ─────────────────────────────────────────
            val gridSnap      = 50
            val gridThreshold = 8f
            val nearX = (newScreenX / gridSnap).roundToInt() * gridSnap.toFloat()
            if (abs(newScreenX - nearX) < gridThreshold && bestXSnap == null) newScreenX = nearX
            val nearY = (newScreenY / gridSnap).roundToInt() * gridSnap.toFloat()
            if (abs(newScreenY - nearY) < gridThreshold && bestYSnap == null) newScreenY = nearY

            // Keep within window bounds
            newScreenX = newScreenX.coerceIn(0f, sw - actualWidth)
            newScreenY = newScreenY.coerceIn(0f, sh - actualHeight)

            // Update anchors
            el.anchorRight  = newScreenX + actualWidth  / 2f > sw / 2f
            el.anchorBottom = newScreenY + actualHeight / 2f > sh / 2f

            el.setScreenX(newScreenX.roundToInt())
            el.setScreenY(newScreenY.roundToInt())
        }

        context.pose().pushMatrix()
        val sf = Telosmancy.mc.window.guiScale
        context.pose().scale(1f / sf.toFloat(), 1f / sf.toFloat())

        // ── Grid overlay ─────────────────────────────────────────────────
        val gridStep  = 50
        val gridColor = 0x1AFFFFFF
        var gx = gridStep
        while (gx < sw) { context.fill(gx, 0, gx + 1, sh, gridColor); gx += gridStep }
        var gy = gridStep
        while (gy < sh) { context.fill(0, gy, sw, gy + 1, gridColor); gy += gridStep }

        // ── Shift center-snap guides ──────────────────────────────────────
        if (isShiftPressed()) {
            val cx = sw / 2
            val cy = sh / 2
            val lineColor = 0x8800FFFF.toInt()
            context.fill(cx, 0, cx + 1, sh, lineColor)
            context.fill(0, cy, sw, cy + 1, lineColor)
        }

        // ── Element renders ───────────────────────────────────────────────
        for (hud in hudSettingsCache) {
            if (hud.isEnabled) hud.value.draw(context, true)
        }

        // ── Element-to-element alignment guides ───────────────────────────
        val guideColor = 0xCC00FF88.toInt()  // green-ish
        for (x in xGuides) context.fill(x.toInt(), 0, x.toInt() + 1, sh, guideColor)
        for (y in yGuides) context.fill(0, y.toInt(), sw, y.toInt() + 1, guideColor)

        // ── Hover tooltip ─────────────────────────────────────────────────
        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hoveredHud ->
            context.pose().pushMatrix()
            context.pose().translate(
                (hoveredHud.value.screenX + hoveredHud.value.width * hoveredHud.value.scale + 10f),
                hoveredHud.value.screenY.toFloat(),
            )
            context.pose().scale(2f, 2f)
            context.text(Telosmancy.mc.font, hoveredHud.name, 0, 0, Colors.WHITE.rgba)
            context.textWithWordWrap(Telosmancy.mc.font, Component.literal(hoveredHud.description), 0, 10, 150, Colors.WHITE.rgba)
            context.pose().popMatrix()
        }

        context.pose().popMatrix()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hovered ->
            val el   = hovered.value
            val step = if (el.scale < 1f) 0.05f else 0.1f
            // Grow/shrink around the element's current center
            val centerX = el.screenX + el.width  * el.scale / 2f
            val centerY = el.screenY + el.height * el.scale / 2f
            el.scale = (el.scale + verticalAmount.sign.toFloat() * step).coerceIn(0.1f, 10f)
            // Re-anchor around center
            val sw = Telosmancy.mc.window.screenWidth
            val sh = Telosmancy.mc.window.screenHeight
            el.setScreenX((centerX - el.width * el.scale / 2f).roundToInt())
            el.setScreenY((centerY - el.height * el.scale / 2f).roundToInt())
            clampElement(el, sw, sh)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hovered ->
            dragging = hovered.value
            deltaX = (hovered.value.screenX - telosmancyMouseX)
            deltaY = (hovered.value.screenY - telosmancyMouseY)
            return true
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        dragging = null
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hovered ->
            val el = hovered.value
            val sw = Telosmancy.mc.window.screenWidth
            val sh = Telosmancy.mc.window.screenHeight
            when (keyEvent.key) {
                GLFW.GLFW_KEY_EQUAL -> { el.scale = (el.scale + 0.1f).coerceIn(0.1f, 10f); clampElement(el, sw, sh) }
                GLFW.GLFW_KEY_MINUS -> { el.scale = (el.scale - 0.1f).coerceIn(0.1f, 10f); clampElement(el, sw, sh) }
                GLFW.GLFW_KEY_RIGHT -> el.setScreenX((el.screenX + 10).coerceIn(0, sw - (el.width * el.scale).toInt()))
                GLFW.GLFW_KEY_LEFT  -> el.setScreenX((el.screenX - 10).coerceIn(0, sw - (el.width * el.scale).toInt()))
                GLFW.GLFW_KEY_UP    -> el.setScreenY((el.screenY - 10).coerceIn(0, sh - (el.height * el.scale).toInt()))
                GLFW.GLFW_KEY_DOWN  -> el.setScreenY((el.screenY + 10).coerceIn(0, sh - (el.height * el.scale).toInt()))
            }
        }
        return super.keyPressed(keyEvent)
    }

    override fun onClose() {
        ModuleManager.saveConfigurations()
        super.onClose()
    }

    fun resetHUDS() {
        hudSettingsCache.forEach {
            it.value.x     = 10
            it.value.y     = 10
            it.value.scale = 2f
        }
    }

    override fun isPauseScreen(): Boolean = false
}
