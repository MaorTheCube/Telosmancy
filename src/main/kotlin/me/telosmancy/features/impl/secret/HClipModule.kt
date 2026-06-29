package me.telosmancy.features.impl.secret

import me.telosmancy.Telosmancy.mc
import me.telosmancy.clickgui.settings.impl.KeybindSetting
import me.telosmancy.clickgui.settings.impl.NumberSetting
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin

object HClipModule : Module(
    name = "H-Clip",
    category = Category.SECRET,
    description = "Teleports you forward through blocks on keypress."
) {
    private val distance by NumberSetting(
        "Distance", 3f, 0.5f, 20f,
        desc = "How many blocks to clip forward."
    )

    private val clipKey by KeybindSetting("Clip Key", GLFW.GLFW_KEY_UNKNOWN, desc = "Key to trigger the clip.")
        .onPress { clip() }

    private fun clip() {
        if (!enabled) return
        val player = mc.player ?: return
        val yawRad = Math.toRadians(player.yRot.toDouble())
        val dx = -sin(yawRad) * distance
        val dz =  cos(yawRad) * distance
        player.setPos(player.x + dx, player.y, player.z + dz)
    }
}
