package me.telosmancy.features.impl.misc

import me.telosmancy.features.Category
import me.telosmancy.features.Module

/**
 * Hide Held Tooltips Module - Disables the item name tooltip that appears when swapping items in the hotbar
 */
object HideHeldTooltipsModule : Module(
    name = "Hide Held Tooltips",
    category = Category.MISC,
    description = "Hides item tooltips when swapping items in the hotbar"
)
