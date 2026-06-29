package me.telosmancy.features.impl.visual.dungeontimer

import me.telosmancy.Telosmancy
import me.telosmancy.utils.data.DungeonData
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.network.chat.Component

/**
 * Extension functions for the Dungeon Timer system.
 */

/**
 * Checks if this dungeon is a multi-stage dungeon with multiple bosses.
 */
fun DungeonData.isMultiStageDungeon(): Boolean =
    name == "RUSTBORN_KINGDOM" || name == "CELESTIALS_PROVINCE"

/**
 * Centers a Component by adding spaces before it based on chat width.
 */
fun centerComponent(component: Component): Component {
    val plainText = component.string
    val textWidth = Telosmancy.mc.font.width(plainText)
    val chatWidth = ChatComponent.getWidth(Telosmancy.mc.options.chatWidth().get())
    
    if (textWidth >= chatWidth) return component
    
    val spacesNeeded = ((chatWidth - textWidth) / 2 / 4).coerceAtLeast(0)
    val spaces = " ".repeat(spacesNeeded)
    
    return Component.literal(spaces).append(component)
}
