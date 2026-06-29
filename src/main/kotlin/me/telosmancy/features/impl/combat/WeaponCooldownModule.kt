package me.telosmancy.features.impl.combat

import me.telosmancy.Telosmancy
import me.telosmancy.clickgui.settings.Setting.Companion.withDependency
import me.telosmancy.clickgui.settings.impl.*
import me.telosmancy.events.TickEvent
import me.telosmancy.events.core.on
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.features.impl.ClickGUIModule
import me.telosmancy.utils.Color
import me.telosmancy.utils.ItemUtils
import me.telosmancy.utils.createSoundSettings
import me.telosmancy.utils.emoji.EmojiReplacer
import me.telosmancy.utils.playSoundSettings
import me.telosmancy.utils.ui.rendering.NVGPIPRenderer
import me.telosmancy.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import kotlin.math.PI

/**
 * Weapon Cooldown Module - visual cooldown ring with title popups.
 */
object WeaponCooldownModule : Module(
    name = "Weapon Cooldown",
    category = Category.COMBAT,
    description = "Displays weapon cooldown with visual notifications."
) {

    private const val INDICATOR_FADE_MS = 250L

    // Color Interpolation Constants
    private const val RED_R = 139f
    private const val RED_G = 0f
    private const val RED_B = 0f
    private const val ORG_R = 250f
    private const val ORG_G = 129f
    private const val ORG_B = 42f

    private val indicatorHud by HUDSetting(
        name = "Cooldown Indicator",
        x = 874,
        y = 524,
        scale = 1f,
        toggleable = true,
        default = true,
        description = "Drag to reposition. Scale to resize the icon and ring.",
        module = this
    ) { example ->
        if (example) {
            val item = displayedWeapon
            if (!item.isEmpty) item(item, 0, 0)
            drawIndicatorPreview(this)
        }
        32 to 32
    }

    private fun drawIndicatorPreview(context: GuiGraphicsExtractor) {
        val gs = ClickGUIModule.getStandardGuiScale()
        val guiScale = mc.window.guiScale.toFloat()
        val guiToNvg = guiScale / gs
        val screenW = mc.window.screenWidth
        val screenH = mc.window.screenHeight
        val hudScale = indicatorHud.scale
        val sX = indicatorHud.screenX.toFloat()
        val sY = indicatorHud.screenY.toFloat()
        val nvgCx = sX / gs + 8f * hudScale * guiToNvg
        val nvgCy = sY / gs + 8f * hudScale * guiToNvg
        val r = 14f * hudScale * guiToNvg
        val thick = 3.5f * hudScale * guiToNvg
        NVGPIPRenderer.draw(context, 0, 0, screenW, screenH) {
            NVGRenderer.scale(gs, gs)
            NVGRenderer.arc(nvgCx, nvgCy, r, 0f, (PI * 2).toFloat(), thick, Color(0, 0, 0, 100f / 255f).rgba)
            NVGRenderer.arc(nvgCx, nvgCy, r, (-PI / 2).toFloat(), (-PI / 2).toFloat() + 0.4f * (PI * 2).toFloat(), thick, getCooldownColor(0.4f))
        }
    }

    private val titleHud by HUDSetting(
        name = "Title Display",
        x = 400,
        y = 200,
        scale = 2f,
        toggleable = true,
        default = true,
        description = "Position of the weapon ready title popup. Toggle to show/hide.",
        module = this
    ) { example ->
        val currentTitle = customTitle
        if (currentTitle == null && !example) return@HUDSetting 0 to 0

        val title = if (example) {
            buildStyledTitleText(titleText, titleColor.rgba)
        } else {
            currentTitle!!
        }

        val textRenderer = mc.font
        val textWidth = textRenderer.width(title)
        val textHeight = textRenderer.lineHeight

        text(textRenderer, title, 0, 0, titleColor.rgba, true)

        textWidth to textHeight
    }

    val titleText: String by StringSetting("Title Text", "Weapon Ready!", desc = "Text to display in title popup").withDependency { titleHud.enabled }
    val duration: Float by NumberSetting("Duration", 60.0f, 10.0f, 100.0f, desc = "Duration of title display in ticks").withDependency { titleHud.enabled }
    val titleColor: Color by ColorSetting("Title Color", Color(0xFF7CFFB2.toInt()), desc = "Color of the title text").withDependency { titleHud.enabled }

    val playSound = registerSetting(BooleanSetting("Play Sound", true, desc = "Play sound when weapon is ready"))
    private val soundSettings = createSoundSettings(
        name = "Sound",
        default = "block.note_block.banjo",
        dependencies = { playSound.value }
    )

    // State Variables
    private var trackedWeapon = ItemStack.EMPTY
    private var displayedWeapon = ItemStack.EMPTY
    private var cooldownProgress = 0f
    private var previousCooldownProgress = 0f
    private var indicatorStartTime = 0L
    private var customTitle: Component? = null
    private var titleDisplayTicks = 0

    init {
        on<TickEvent.End> {
            if (!enabled) return@on

            val player = mc.player ?: return@on

            val heldWeapon = getCurrentPlayerWeapon(player)
            displayedWeapon = heldWeapon

            if (!heldWeapon.isEmpty && !ItemStack.isSameItemSameComponents(heldWeapon, trackedWeapon)) {
                trackedWeapon = heldWeapon.copy()
            }

            if (!trackedWeapon.isEmpty) {
                previousCooldownProgress = cooldownProgress
                cooldownProgress = player.cooldowns.getCooldownPercent(trackedWeapon, 0f)

                if (previousCooldownProgress > 0f && cooldownProgress == 0f) {
                    indicatorStartTime = System.currentTimeMillis()

                    if (titleHud.enabled) {
                        customTitle = buildStyledTitleText(titleText, titleColor.rgba)
                        titleDisplayTicks = duration.toInt()
                    }

                    if (playSound.enabled) {
                        playNotificationSound()
                    }
                }
            }

            if (previousCooldownProgress == 0f && cooldownProgress > 0f) {
                titleDisplayTicks = 0
                customTitle = null
            }

            if (titleDisplayTicks > 0) {
                titleDisplayTicks--
                if (titleDisplayTicks <= 0) {
                    customTitle = null
                }
            }
        }
    }

    private fun getCooldownColor(progress: Float): Int {
        val r = (ORG_R + (RED_R - ORG_R) * progress).toInt()
        val g = (ORG_G + (RED_G - ORG_G) * progress).toInt()
        val b = (ORG_B + (RED_B - ORG_B) * progress).toInt()
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun renderHud(context: GuiGraphicsExtractor) {
        if (!enabled || !indicatorHud.enabled || displayedWeapon.isEmpty) return

        val gs = ClickGUIModule.getStandardGuiScale()
        val guiScale = mc.window.guiScale.toFloat()
        val guiToNvg = guiScale / gs
        val screenW = mc.window.screenWidth
        val screenH = mc.window.screenHeight

        val elem = indicatorHud
        val hudScale = elem.scale
        val sX = elem.screenX.toFloat()
        val sY = elem.screenY.toFloat()

        // Item icon — scale around its top-left corner in GUI space
        val iconX = (sX / guiScale).toInt()
        val iconY = (sY / guiScale).toInt()
        context.pose().pushMatrix()
        context.pose().translate(iconX.toFloat(), iconY.toFloat())
        context.pose().scale(hudScale, hudScale)
        context.item(displayedWeapon, 0, 0)
        context.pose().popMatrix()

        // Arc center: icon top-left in NVG units + half-icon offset scaled
        val nvgCx = sX / gs + 8f * hudScale * guiToNvg
        val nvgCy = sY / gs + 8f * hudScale * guiToNvg
        val r = 14f * hudScale * guiToNvg
        val thick = 3.5f * hudScale * guiToNvg

        val progress = cooldownProgress.coerceIn(0f, 1f)
        val timeSinceReady = System.currentTimeMillis() - indicatorStartTime
        val readyAlpha = if (indicatorStartTime > 0 && timeSinceReady < INDICATOR_FADE_MS)
            (1f - timeSinceReady.toFloat() / INDICATOR_FADE_MS).coerceIn(0f, 1f) else 0f

        NVGPIPRenderer.draw(context, 0, 0, screenW, screenH) {
            NVGRenderer.scale(gs, gs)
            NVGRenderer.arc(nvgCx, nvgCy, r, 0f, (PI * 2).toFloat(), thick, Color(0, 0, 0, 100f / 255f).rgba)
            if (progress > 0f) {
                val startA = (-PI / 2).toFloat()
                NVGRenderer.arc(nvgCx, nvgCy, r, startA, startA + progress * (PI * 2).toFloat(), thick, getCooldownColor(progress))
            }
            if (readyAlpha > 0f) {
                NVGRenderer.arc(nvgCx, nvgCy, r, 0f, (PI * 2).toFloat(), thick, Color(0, 220, 100, readyAlpha).rgba)
            }
        }
    }

    private fun buildStyledTitleText(text: String, textColor: Int): Component {
        if (text.isEmpty()) return Component.literal("")
        return EmojiReplacer.replaceIn(Component.literal(text).withStyle(Style.EMPTY.withColor(textColor)))
    }

    private fun playNotificationSound() {
        try {
            playSoundSettings(soundSettings())
        } catch (e: Exception) {
            Telosmancy.logger.warn("Weapon Cooldown: Failed to play sound: ${e.message}")
        }
    }

    private fun getCurrentPlayerWeapon(player: LocalPlayer): ItemStack {
        val mainHandStack = player.mainHandItem
        if (ItemUtils.isWeapon(mainHandStack)) return mainHandStack
        return ItemStack.EMPTY
    }

    override fun onDisable() {
        super.onDisable()
        trackedWeapon = ItemStack.EMPTY
        displayedWeapon = ItemStack.EMPTY
        cooldownProgress = 0f
        previousCooldownProgress = 0f
        customTitle = null
        titleDisplayTicks = 0
        indicatorStartTime = 0L
    }
}
