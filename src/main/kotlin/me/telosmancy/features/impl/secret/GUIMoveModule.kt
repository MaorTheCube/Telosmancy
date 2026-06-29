package me.telosmancy.features.impl.secret

import me.telosmancy.features.Category
import me.telosmancy.features.Module

object GUIMoveModule : Module(
    name = "GUI Move",
    category = Category.SECRET,
    description = "Allows WASD movement while any GUI screen is open."
) {
    @JvmStatic
    fun isModuleEnabled(): Boolean = enabled
}
