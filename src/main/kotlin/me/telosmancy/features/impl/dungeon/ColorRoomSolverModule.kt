package me.telosmancy.features.impl.dungeon

import me.telosmancy.clickgui.settings.impl.HUDSetting
import me.telosmancy.events.ChatPacketEvent
import me.telosmancy.events.WorldLoadEvent
import me.telosmancy.events.core.on
import me.telosmancy.events.core.onReceive
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.utils.Color
import me.telosmancy.utils.noControlCodes
import me.telosmancy.utils.render.textDim
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket

object ColorRoomSolverModule : Module(
    name = "Color Room Solver",
    category = Category.DUNGEON,
    description = "Detects R/G/B particles in the Dreadwood Thicket color room and shows the sequence to press."
) {
    private val COLOR_RED   = Color(0xFFFF4455.toInt())
    private val COLOR_GREEN = Color(0xFF44FF66.toInt())
    private val COLOR_BLUE  = Color(0xFF4488FF.toInt())

    private const val LETTER_SCALE = 4f
    private const val LETTER_GAP   = 3

    // Only true between "is attempting" and "has completed" chat messages.
    private var detecting = false
    private val sequence = mutableListOf<Char>()
    private val seqLock = Any()

    private val hud by HUDSetting(
        name = "Color Room", x = 10, y = 10, scale = 1f, toggleable = false,
        description = "Position of the Color Room Solver display.", module = this
    ) render@{ example ->
        if (!enabled && !example) return@render 0 to 0

        val seq: List<Char>
        if (example) {
            seq = listOf('G', 'G', 'R')
        } else {
            if (!detecting) return@render 0 to 0
            seq = synchronized(seqLock) { sequence.toList() }
            if (seq.isEmpty()) return@render 0 to 0
        }

        pose().pushMatrix()
        pose().scale(LETTER_SCALE, LETTER_SCALE)
        var x = 0
        for (ch in seq) {
            val (cw, _) = textDim(ch.toString(), x, 0, colorFor(ch), shadow = true)
            x += cw + LETTER_GAP
        }
        pose().popMatrix()

        val totalW = ((x - LETTER_GAP).coerceAtLeast(0) * LETTER_SCALE).toInt()
        val totalH = (mc.font.lineHeight * LETTER_SCALE).toInt()
        totalW to totalH
    }

    private fun colorFor(ch: Char) = when (ch) {
        'R' -> COLOR_RED
        'G' -> COLOR_GREEN
        else -> COLOR_BLUE
    }

    init {
        on<ChatPacketEvent> {
            if (!enabled) return@on
            val ign = mc.player?.gameProfile?.name ?: return@on
            val clean = value.noControlCodes.trim()
            if (!clean.contains("[Dreadwood Civilian]")) return@on
            when {
                clean.contains("$ign is attempting the Colour Room") -> {
                    clearState()
                    detecting = true
                }
                clean.contains("$ign has completed the Colour Room") -> {
                    clearState()
                }
            }
        }

        onReceive<ClientboundLevelParticlesPacket> {
            if (!enabled || !detecting) return@onReceive
            if (particle.type != ParticleTypes.DUST) return@onReceive
            val dust = particle as? DustParticleOptions ?: return@onReceive
            val player = mc.player ?: return@onReceive
            val dx = x - player.x; val dy = y - player.y; val dz = z - player.z
            if (dx * dx + dy * dy + dz * dz > 900.0) return@onReceive
            val ch = classifyDust(dust) ?: return@onReceive
            synchronized(seqLock) {
                if (sequence.isEmpty() || sequence.last() != ch) {
                    sequence.add(ch)
                }
            }
        }

        on<WorldLoadEvent> { clearState() }
    }

    private fun classifyDust(dust: DustParticleOptions): Char? {
        val c = dust.color; val r = c.x; val g = c.y; val b = c.z
        val min = 0.5f; val gap = 0.35f
        return when {
            r > min && r - g > gap && r - b > gap -> 'R'
            g > min && g - r > gap && g - b > gap -> 'G'
            b > min && b - r > gap && b - g > gap -> 'B'
            else -> null
        }
    }

    private fun clearState() {
        synchronized(seqLock) { sequence.clear() }
        detecting = false
    }
}
