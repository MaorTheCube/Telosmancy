package me.telosmancy.clickgui.settings

import me.telosmancy.clickgui.ClickGUI
import me.telosmancy.clickgui.Panel
import me.telosmancy.clickgui.settings.impl.ColorSetting
import me.telosmancy.features.Module
import me.telosmancy.features.impl.ClickGUIModule
import me.telosmancy.utils.Colors
import me.telosmancy.utils.ui.HoverHandler
import me.telosmancy.utils.ui.animations.EaseInOutAnimation
import me.telosmancy.utils.ui.animations.LinearAnimation
import me.telosmancy.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.floor

class ModuleButton(val module: Module) {

    val representableSettings = module.settings.values.mapNotNull { it as? RenderableSetting }

    var extended = false

    private val toggleAnim = LinearAnimation<Float>(160)
    private val extendAnim = EaseInOutAnimation(220)
    private val rowHover = HoverHandler(120)
    private val hoverHandler = HoverHandler(750)

    fun getHeight(): Float = ClickGUI.ROW_H + floor(extendAnim.get(0f, settingsHeight(), !extended))

    private fun settingsHeight(): Float =
        representableSettings.filter { it.isVisible }.sumOf { it.getHeight().toDouble() }.toFloat()

    fun draw(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        rowHover.handle(x, y, w, ClickGUI.ROW_H, true)
        hoverHandler.handle(x, y, w, ClickGUI.ROW_H, true)

        if (hoverHandler.percent() >= 100)
            ClickGUI.setDescription(module.description, x + w + 10f, y, hoverHandler)

        val rowBg = if (rowHover.isHovered) ClickGUI.ROW_HOVER else ClickGUI.ROW_NORMAL
        NVGRenderer.rect(x, y, w, ClickGUI.ROW_H, rowBg, 6f)

        if (module.enabled)
            NVGRenderer.rect(x, y, 3f, ClickGUI.ROW_H, ClickGUIModule.clickGUIColor.rgba, 1.5f)

        val nameColor = if (module.enabled) Colors.WHITE.rgba else ClickGUI.TEXT_SEC
        NVGRenderer.text(module.name, x + 14f, y + ClickGUI.ROW_H / 2f - 8f, 14f, nameColor, NVGRenderer.defaultFont)

        if (representableSettings.isNotEmpty()) {
            val indColor = if (extended) ClickGUIModule.clickGUIColor.rgba else ClickGUI.TEXT_SEC
            NVGRenderer.text(if (extended) "−" else "+", x + w - 70f, y + ClickGUI.ROW_H / 2f - 7f, 12f, indColor, NVGRenderer.defaultFont)
        }

        val tx = x + w - 50f
        val ty = y + ClickGUI.ROW_H / 2f - 9f
        NVGRenderer.rect(tx, ty, 36f, 18f, if (module.enabled) ClickGUIModule.clickGUIColor.rgba else ClickGUI.TOGGLE_OFF, 9f)
        val circleX = tx + toggleAnim.get(9f, 27f, !module.enabled)
        NVGRenderer.circle(circleX, ty + 9f, 6f, Colors.WHITE.rgba)

        if (extendAnim.isAnimating() || extended) {
            val totalH = ClickGUI.ROW_H + floor(extendAnim.get(0f, settingsHeight(), !extended))
            if (extendAnim.isAnimating()) NVGRenderer.pushScissor(x, y, w, totalH)

            NVGRenderer.rect(x, y + ClickGUI.ROW_H, w, 1f, ClickGUI.SEP)
            val settingsH = settingsHeight()
            if (settingsH > 0f) NVGRenderer.rect(x, y + ClickGUI.ROW_H + 1f, w, settingsH, ClickGUI.SIDEBAR_BG)

            Panel.contentWidth = w
            var drawY = ClickGUI.ROW_H + 1f
            for (setting in representableSettings) {
                if (!setting.isVisible) continue
                drawY += setting.render(x, y + drawY, mouseX, mouseY)
            }

            if (extendAnim.isAnimating()) NVGRenderer.popScissor()
        }
    }

    fun mouseClicked(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (rowHover.isHovered) {
            when (click.button()) {
                0 -> { toggleAnim.start(); module.toggle(); return true }
                1 -> {
                    if (representableSettings.isNotEmpty()) { extendAnim.start(); extended = !extended }
                    return true
                }
            }
        } else if (extended) {
            for (setting in representableSettings) {
                if (setting.isVisible && setting.mouseClicked(mouseX, mouseY, click)) return true
            }
        }
        return false
    }

    fun mouseReleased(click: MouseButtonEvent) {
        if (!extended) return
        for (setting in representableSettings) {
            if (setting.isVisible) setting.mouseReleased(click)
        }
    }

    fun keyTyped(input: CharacterEvent): Boolean {
        if (!extended) return false
        return representableSettings.any { it.isVisible && it.keyTyped(input) }
    }

    fun keyPressed(input: KeyEvent): Boolean {
        if (!extended) return false
        return representableSettings.any { it.isVisible && it.keyPressed(input) }
    }
}
