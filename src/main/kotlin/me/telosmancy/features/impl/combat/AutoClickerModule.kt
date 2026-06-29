package me.telosmancy.features.impl.combat

import me.telosmancy.events.TickEvent
import me.telosmancy.events.core.on
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.clickgui.settings.impl.BooleanSetting
import net.minecraft.world.InteractionHand

/**
 * Auto Clicker Module - Automatically clicks when attack key is held.
 * 
 * When enabled and attacking, it will continuously click.
 * If "Only If No Cooldown" is enabled, it will only fire when the item's cooldown is available.
 */
object AutoClickerModule : Module(
    name = "Auto Clicker",
    category = Category.COMBAT,
    description = "Automatically clicks for you when conditions are met."
) {

    // Settings
    val onlyIfNoCooldown = registerSetting(BooleanSetting("Only If No Cooldown", true, desc = "Only click when no cooldown"))

    init {
        // Register event handler using EventBus
        on<TickEvent.End> {
            if (!enabled) return@on
            
            val player = mc.player ?: return@on
            
            // Check if attack key is pressed
            // Use Minecraft's options to check if attack key is down
            val isAttackPressed = mc.options.keyAttack.isDown
            
            if (!isAttackPressed) return@on
            
            // Check cooldown if setting is enabled
            if (onlyIfNoCooldown.enabled) {
                val mainHandStack = player.mainHandItem
                val canSwing = !player.cooldowns.isOnCooldown(mainHandStack)
                
                // Only swing if not on cooldown
                if (canSwing) {
                    player.swing(InteractionHand.MAIN_HAND)
                }
            } else {
                // Continuous clicking when cooldown check is disabled
                player.swing(InteractionHand.MAIN_HAND)
            }
        }
    }
}
