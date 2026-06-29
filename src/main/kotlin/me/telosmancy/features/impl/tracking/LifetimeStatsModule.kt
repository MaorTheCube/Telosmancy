package me.telosmancy.features.impl.tracking

import me.telosmancy.clickgui.settings.impl.BooleanSetting
import me.telosmancy.clickgui.settings.impl.ColorSetting
import me.telosmancy.clickgui.settings.impl.HUDSetting
import me.telosmancy.utils.render.FIRE_TITLE_COLOR
import me.telosmancy.utils.render.drawFireFrame
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.utils.Color
import me.telosmancy.utils.data.persistence.DataConfig
import me.telosmancy.utils.data.persistence.TrackingKey
import me.telosmancy.utils.data.persistence.TypeSafeDataAccess
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Lifetime Stats Module - Displays lifetime statistics for bag drops
 */
object LifetimeStatsModule : Module(
    name = "Lifetime Stats",
    category = Category.TRACKING,
    description = "Displays lifetime statistics for bag drops and runs"
) {

    // Color setting for border and title
    private val widgetColor by ColorSetting("Widget Color", Color(0xFF2E8F78.toInt()), desc = "Color for the widget border and title")
    private val labelColor by ColorSetting("Label Color", Color(0xFF888888.toInt()), desc = "Color for the bag labels")
    private val valueColor by ColorSetting("Value Color", Color(0xFFFFFFFF.toInt()), desc = "Color for the bag amounts")

    // Toggleable stats (in display order)
    private val showEventBags by BooleanSetting("Event Bags", true, desc = "Display event bags counter")
    private val showCompanionBags by BooleanSetting("Companion Bags", true, desc = "Display companion bags counter")
    private val showRoyalBags by BooleanSetting("Royal Bags", true, desc = "Display royal bags counter")
    private val showBloodshotBags by BooleanSetting("Bloodshot Bags", true, desc = "Display bloodshot bags counter")
    private val showVoidboundBags by BooleanSetting("Voidbound Bags", true, desc = "Display voidbound bags counter")
    private val showUnholyBags by BooleanSetting("Unholy Bags", true, desc = "Display unholy bags counter")
    private val showTotalRuns by BooleanSetting("Total Runs", true, desc = "Display total runs counter")

    // Cached values for instant updates
    private var cachedTotalRuns = 0
    private var cachedBloodshotBags = 0
    private var cachedUnholyBags = 0
    private var cachedVoidboundBags = 0
    private var cachedRoyalBags = 0
    private var cachedCompanionBags = 0
    private var cachedEventBags = 0

    // Render loop state tracking
    private var forceRenderUpdate = true
    private var wasExample = false
    private var lastSettingsState = -1
    
    // Dimension caching to avoid per-frame Pair allocation
    private var lastRenderWidth = 0
    private var lastRenderHeight = 0
    private var cachedRenderPair = Pair(0, 0)
    
    // Title caching
    private var cachedTitleComponent: Component? = null
    private var cachedTitleWidth = 0
    
    // Fallback caching
    private var noDataWidth = -1
    
    // Data class to store pre-calculated strings and text widths
    private class CachedStat(
        val labelText: String,
        val labelWidth: Int,
        var valueText: String,
        var valueWidth: Int,
        val color: Int = 0xFFFFFFFF.toInt()
    )
    private val statRenderList = mutableListOf<CachedStat>()
    
    init {
        // Register callback for instant updates when stats change
        DataConfig.registerUpdateCallback {
            updateCache()
        }
        
        // Initial cache load
        updateCache()
    }

    private fun updateCache() {
        cachedTotalRuns = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.TotalRuns) ?: 0
        cachedBloodshotBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.BloodshotBags) ?: 0
        cachedUnholyBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.UnholyBags) ?: 0
        cachedVoidboundBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.VoidboundBags) ?: 0
        cachedRoyalBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.RoyalBags) ?: 0
        cachedCompanionBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.CompanionBags) ?: 0
        cachedEventBags = TypeSafeDataAccess.get(TrackingKey.LifetimeStat.EventBags) ?: 0
        
        // Flag to rebuild the UI text cache on the next frame
        forceRenderUpdate = true
    }
    
    /**
     * Packs all boolean settings into a single bitmask integer
     */
    private fun getCurrentSettingsState(): Int {
        return (if (showEventBags) 1 else 0) or
                (if (showCompanionBags) 2 else 0) or
                (if (showRoyalBags) 4 else 0) or
                (if (showBloodshotBags) 8 else 0) or
                (if (showVoidboundBags) 16 else 0) or
                (if (showUnholyBags) 32 else 0) or
                (if (showTotalRuns) 64 else 0)
    }
    
    /**
     * Builds the render cache only when stats update, settings change, or in example mode
     */
    private fun updateRenderData(example: Boolean) {
        val font = mc.font
        statRenderList.clear()
        
        // Local helper to avoid repeating width logic
        fun addStat(label: String, value: Int, color: Int = 0xFFFFFFFF.toInt()) {
            val labelText = "$label:"
            val valueText = value.toString()
            statRenderList.add(
                CachedStat(
                    labelText = labelText,
                    labelWidth = font.width(labelText),
                    valueText = valueText,
                    valueWidth = font.width(valueText),
                    color = color
                )
            )
        }

        val C_EVENT     = 0xFFFFAA00.toInt()  // gold-orange (event)
        val C_COMPANION = 0xFFFFAA00.toInt()  // same (companion)
        val C_ROYAL     = 0xFFCC44FF.toInt()  // bright purple
        val C_BLOODSHOT = 0xFFDD3344.toInt()  // red
        val C_VOID      = 0xFF9933FF.toInt()  // violet
        val C_UNHOLY    = 0xFFCCCCCC.toInt()  // light gray
        val C_RUNS      = 0xFF55CCFF.toInt()  // sky blue

        if (example) {
            if (showEventBags) addStat("Events", 7, C_EVENT)
            if (showCompanionBags) addStat("Companions", 15, C_COMPANION)
            if (showRoyalBags) addStat("Royal", 23, C_ROYAL)
            if (showBloodshotBags) addStat("Bloodshot", 56, C_BLOODSHOT)
            if (showVoidboundBags) addStat("Voidbound", 8, C_VOID)
            if (showUnholyBags) addStat("Unholy", 12, C_UNHOLY)
            if (showTotalRuns) addStat("Total Runs", 1234, C_RUNS)
        } else {
            if (showEventBags) addStat("Events", cachedEventBags, C_EVENT)
            if (showCompanionBags) addStat("Companions", cachedCompanionBags, C_COMPANION)
            if (showRoyalBags) addStat("Royals", cachedRoyalBags, C_ROYAL)
            if (showBloodshotBags) addStat("Bloodshots", cachedBloodshotBags, C_BLOODSHOT)
            if (showVoidboundBags) addStat("Voidbounds", cachedVoidboundBags, C_VOID)
            if (showUnholyBags) addStat("Unholys", cachedUnholyBags, C_UNHOLY)
            if (showTotalRuns) addStat("Total Runs", cachedTotalRuns, C_RUNS)
        }
    }

    private val lifetimeStatsHud by HUDSetting(
        name = "Lifetime Stats Display",
        x = 10,
        y = 100,
        scale = 1f,
        toggleable = false,
        description = "Position of the lifetime stats display",
        module = this
    ) render@{ example ->
        if (!enabled && !example) return@render cachedRenderPair
        
        val font = mc.font

        // Check if we need to rebuild the cache
        val currentSettingsState = getCurrentSettingsState()
        if (forceRenderUpdate || currentSettingsState != lastSettingsState || example != wasExample) {
            updateRenderData(example)
            
            lastSettingsState = currentSettingsState
            wasExample = example
            forceRenderUpdate = false
        }
        
        // Ensure title is cached
        if (cachedTitleComponent == null) {
            cachedTitleComponent = Component.literal("Lifetime Stats").withStyle(ChatFormatting.BOLD)
            cachedTitleWidth = font.width(cachedTitleComponent!!)
        }
        
        // Calculate dimensions
        val headerH = font.lineHeight + 7
        val lineSpacing = 11
        var maxLabelWidth = 0
        var maxValueWidth = 0

        if (statRenderList.isNotEmpty()) {
            for (i in 0 until statRenderList.size) {
                val stat = statRenderList[i]
                if (stat.labelWidth > maxLabelWidth) maxLabelWidth = stat.labelWidth
                if (stat.valueWidth > maxValueWidth) maxValueWidth = stat.valueWidth
            }
        } else {
            if (noDataWidth == -1) noDataWidth = font.width("No stats data")
            maxLabelWidth = noDataWidth
        }

        val contentWidth = maxLabelWidth + maxValueWidth + 10
        val boxWidth = maxOf(cachedTitleWidth + 16, contentWidth + 14)
        val boxHeight = headerH + 2 + (maxOf(statRenderList.size, 1) * lineSpacing) + 5

        // Update returned dimension pair cache if dimensions changed
        if (boxWidth != lastRenderWidth || boxHeight != lastRenderHeight) {
            lastRenderWidth = boxWidth
            lastRenderHeight = boxHeight
            cachedRenderPair = Pair(boxWidth, boxHeight)
        }

        drawFireFrame(boxWidth, boxHeight, headerH)
        text(font, cachedTitleComponent!!, 8, 5, FIRE_TITLE_COLOR, true)

        // Draw stats
        if (statRenderList.isNotEmpty()) {
            var yOffset = headerH + 2
            val leftPadding = 8
            val rightPadding = 6

            for (i in 0 until statRenderList.size) {
                if (i % 2 == 0) fill(0, yOffset - 1, boxWidth, yOffset + lineSpacing - 1, 0x08FFFFFF)
                val stat = statRenderList[i]

                text(font, stat.labelText, leftPadding, yOffset, labelColor.rgba, false)

                val valueX = boxWidth - stat.valueWidth - rightPadding
                text(font, stat.valueText, valueX, yOffset, stat.color, true)

                yOffset += lineSpacing
            }
        } else {
            text(font, "No stats data", 8, headerH + 2, 0xFF808080.toInt(), false)
        }
        
        return@render cachedRenderPair
    }
}