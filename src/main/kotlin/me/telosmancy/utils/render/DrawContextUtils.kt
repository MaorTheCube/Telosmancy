package me.telosmancy.utils.render

import me.telosmancy.Telosmancy
import me.telosmancy.utils.Color
import me.telosmancy.utils.Colors
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix3x2f
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

fun GuiGraphicsExtractor.text(text: String, x: Int, y: Int, color: Color = Colors.WHITE, shadow: Boolean = true) {
    text(Telosmancy.mc.font, text, x, y, color.rgba, shadow)
}

fun GuiGraphicsExtractor.textDim(text: String, x: Int, y: Int, color: Color = Colors.WHITE, shadow: Boolean = true): Pair<Int, Int> {
    text(text, x, y, color, shadow)
    return Telosmancy.mc.font.width(text) to Telosmancy.mc.font.lineHeight
}

fun GuiGraphicsExtractor.text(text: FormattedCharSequence, x: Int, y: Int, color: Color = Colors.WHITE, shadow: Boolean = true) {
    text(Telosmancy.mc.font, text, x, y, color.rgba, shadow)
}

fun getStringWidth(text: String): Int = Telosmancy.mc.font.width(text)

fun GuiGraphicsExtractor.hollowFill(x: Int, y: Int, width: Int, height: Int, thickness: Int, color: Color) {
    fill(x, y, x + width, y + thickness, color.rgba)
    fill(x, y + height - thickness, x + width, y + height, color.rgba)
    fill(x, y + thickness, x + thickness, y + height - thickness, color.rgba)
    fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color.rgba)
}

val FIRE_TITLE_COLOR = 0xFFFFFFFF.toInt()

fun GuiGraphicsExtractor.drawHorizGradient(x: Int, y: Int, w: Int, h: Int, colorA: Int, colorB: Int) {
    if (w <= 0) return
    val steps = w.coerceAtMost(32)
    val aA = (colorA ushr 24) and 0xFF; val rA = (colorA shr 16) and 0xFF
    val gA = (colorA shr 8) and 0xFF;  val bA = colorA and 0xFF
    val aB = (colorB ushr 24) and 0xFF; val rB = (colorB shr 16) and 0xFF
    val gB = (colorB shr 8) and 0xFF;  val bB = colorB and 0xFF
    for (i in 0 until steps) {
        val t = if (steps == 1) 0f else i.toFloat() / (steps - 1)
        val a = (aA + (aB - aA) * t + 0.5f).toInt().coerceIn(0, 255)
        val r = (rA + (rB - rA) * t + 0.5f).toInt().coerceIn(0, 255)
        val g = (gA + (gB - gA) * t + 0.5f).toInt().coerceIn(0, 255)
        val b = (bA + (bB - bA) * t + 0.5f).toInt().coerceIn(0, 255)
        val x1 = x + i * w / steps
        val x2 = if (i == steps - 1) x + w else x + (i + 1) * w / steps
        fill(x1, y, x2, y + h, (a shl 24) or (r shl 16) or (g shl 8) or b)
    }
}

fun GuiGraphicsExtractor.drawFireFrame(width: Int, height: Int, headerH: Int) {
    fill(0, 0, width, height, 0xEA101010.toInt())
    val half = width / 2
    drawHorizGradient(0, 0, half, 3, 0xFF1E0700.toInt(), 0xFFB84800.toInt())
    drawHorizGradient(half, 0, width - half, 3, 0xFFB84800.toInt(), 0xFF1E0700.toInt())
    fill(0, headerH, width, headerH + 1, 0xFF1E1E1E.toInt())
}

fun GuiGraphicsExtractor.drawLine(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    color: Color,
    lineWidth: Float = 1f
) {
    val dx = x2 - x1
    val dy = y2 - y1

    val half = max(1, (lineWidth / 2f).toInt())

    pose().pushMatrix()
    pose().translate(x1, y1)
    pose().mul(Matrix3x2f().identity().rotate(atan2(dy, dx)))
    fill(0, -half, ceil(hypot(dx, dy)).toInt(), half, color.rgba)
    pose().popMatrix()
}
