package me.telosmancy.features.impl.visual

import me.telosmancy.features.Category
import me.telosmancy.features.Module

/**
 * Fullbright module using ambient light modification
 * Based on NoFrills' Ambient mode - cleanest fullbright with no visual overlay
 */
object FullbrightModule : Module(
    name = "Fullbright",
    category = Category.VISUAL,
    description = "Provides full brightness using ambient light"
)
