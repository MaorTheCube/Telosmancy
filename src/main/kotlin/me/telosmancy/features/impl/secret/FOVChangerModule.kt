package me.telosmancy.features.impl.secret

import me.telosmancy.clickgui.settings.impl.NumberSetting
import me.telosmancy.features.Category
import me.telosmancy.features.Module

object FOVChangerModule : Module(
    name = "FOV Changer",
    category = Category.SECRET,
    description = "Adds extra FOV on top of your Minecraft FOV setting."
) {
    private val fovAddition by NumberSetting(
        "Extra FOV", 0f, -50f, 90f,
        desc = "FOV to add on top of your Minecraft FOV setting."
    )

    @JvmStatic
    fun getExtraFov(): Double = if (enabled) fovAddition.toDouble() else 0.0
}
