package me.telosmancy.features.impl.visual

import me.telosmancy.Telosmancy.mc
import me.telosmancy.clickgui.settings.impl.BooleanSetting
import me.telosmancy.clickgui.settings.impl.ColorSetting
import me.telosmancy.clickgui.settings.impl.NumberSetting
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.features.ModuleManager
import me.telosmancy.features.impl.ClickGUIModule
import me.telosmancy.utils.Color
import me.telosmancy.utils.ui.rendering.NVGPIPRenderer
import me.telosmancy.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor

object ModuleListModule : Module(
    name = "Module List",
    category = Category.VISUAL,
    description = "Displays all enabled modules in the top-right corner."
) {
    private val textSize by NumberSetting("Text Size", 12f, 8f, 18f, desc = "Font size of each module name.")
    private val lineSpacing by NumberSetting("Line Spacing", 4f, 0f, 12f, desc = "Vertical gap between module names.")
    private val margin by NumberSetting("Margin", 8f, 2f, 24f, desc = "Distance from screen edges.")
    private val background by BooleanSetting("Background", true, desc = "Draw a dark backdrop behind each module name.")
    private val accentBar by BooleanSetting("Accent Bar", true, desc = "Show a colored bar to the right of each module name.")
    private val accentColor by ColorSetting("Accent Color", Color(27, 197, 97), desc = "Color of the right-side accent bar.")

    fun renderHud(context: GuiGraphicsExtractor) {
        if (!enabled) return

        val gs = ClickGUIModule.getStandardGuiScale()
        val screenW = mc.window.screenWidth
        val screenH = mc.window.screenHeight

        val enabledModules = ModuleManager.modules.values
            .filter { it.enabled && it.category != Category.SECRET && it !== this && it !== ClickGUIModule }
            .sortedBy { it.name }

        if (enabledModules.isEmpty()) return

        NVGPIPRenderer.draw(context, 0, 0, screenW, screenH) {
            NVGRenderer.scale(gs, gs)
            val nvgW = screenW / gs
            val m = margin
            val sz = textSize
            val ls = lineSpacing
            val barW = 3f
            val barPad = 4f

            var y = m
            for (module in enabledModules) {
                val tw = NVGRenderer.textWidth(module.name, sz, NVGRenderer.defaultFont)
                val rowH = sz + ls
                val x = nvgW - m - tw - if (accentBar) (barPad + barW) else 0f

                if (background) {
                    val bgX = x - 5f
                    val bgW = tw + 10f + if (accentBar) (barPad + barW) else 0f
                    NVGRenderer.rect(bgX, y - 2f, bgW, sz + 4f, Color(0, 0, 0, 110f / 255f).rgba, 3f)
                }

                NVGRenderer.text(module.name, x, y, sz, Color(210, 215, 235).rgba, NVGRenderer.defaultFont)

                if (accentBar) {
                    NVGRenderer.rect(nvgW - m - barW, y - 1f, barW, sz + 2f, accentColor.rgba, barW / 2f)
                }

                y += rowH
            }
        }
    }
}
