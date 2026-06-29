package me.telosmancy.features.impl.secret

import me.telosmancy.Telosmancy.mc
import me.telosmancy.clickgui.settings.impl.NumberSetting
import me.telosmancy.events.TickEvent
import me.telosmancy.events.core.on
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

object SpeedBoostModule : Module(
    name = "Speed Boost",
    category = Category.SECRET,
    description = "Multiplies your movement speed client-side."
) {
    private val speedPercent by NumberSetting(
        "Speed %", 50f, 1f, 100f,
        desc = "Speed increase as a percentage of base speed. 100 = double speed."
    )

    private val MODIFIER_ID = Identifier.fromNamespaceAndPath("telosmancy", "speed_boost")
    private const val BASE_WALK_SPEED = 0.1f

    init {
        on<TickEvent.End> {
            val player = mc.player ?: return@on
            val attr = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return@on
            if (!enabled) {
                if (attr.getModifier(MODIFIER_ID) != null) {
                    attr.removeModifier(MODIFIER_ID)
                    player.abilities.walkingSpeed = BASE_WALK_SPEED
                }
                return@on
            }
            val targetMultiplier = speedPercent / 100.0
            val existing = attr.getModifier(MODIFIER_ID)
            if (existing == null || existing.amount != targetMultiplier) {
                attr.removeModifier(MODIFIER_ID)
                attr.addTransientModifier(
                    AttributeModifier(MODIFIER_ID, targetMultiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                )
                // Mirror the multiplier into walkingSpeed so GameRenderer's FOV ratio stays 1:1
                player.abilities.walkingSpeed = BASE_WALK_SPEED * (1f + speedPercent / 100f)
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        mc.player?.also { player ->
            player.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
            player.abilities.walkingSpeed = BASE_WALK_SPEED
        }
    }
}
