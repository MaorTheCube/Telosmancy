package me.telosmancy.features.impl.visual

import me.telosmancy.clickgui.settings.Setting.Companion.withDependency
import me.telosmancy.clickgui.settings.impl.*
import me.telosmancy.events.RenderEvent
import me.telosmancy.events.core.on
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.utils.render.TriangleStripData
import me.telosmancy.utils.render.TriangleStripPoint
import me.telosmancy.utils.Color
import com.mojang.math.Axis
import me.telosmancy.utils.renderBoundingBox
import net.minecraft.client.CameraType
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 0 = Rounded, 1 = Flat, 2 = Circle (3D world ring), 3 = Segmented
private const val ROUNDED   = 0
private const val FLAT      = 1
private const val CIRCLE    = 2
private const val SEGMENTED = 3

// Index into RenderConsumer lists: 0 = depth-tested, 1 = always visible
private const val NO_DEPTH  = 1

// Bar world-space constants
private const val BAR_WORLD_SCALE  = 0.01   // 1 GUI pixel = 0.01 world blocks
private const val BAR_WORLD_OFFSET = 0.12   // blocks above player's feet (keeps bar above ground)

object PlayerHPBarModule : Module(
    name = "Player HP Bar",
    category = Category.VISUAL,
    description = "A customizable health bar with multiple visual styles, all rendered in 3D world space."
) {

    // ── Style ─────────────────────────────────────────────────────────────
    private val style by SelectorSetting(
        "Style", "Rounded",
        listOf("Rounded", "Flat", "Circle", "Segmented"),
        desc = "Visual style of the bar"
    )

    // ── Bar dimensions ────────────────────────────────────────────────────
    private val barWidth  by NumberSetting("Width",  120.0, 40.0, 400.0, 1.0, desc = "Width of the bar (pixels → ×0.01 blocks in world)")
        .withDependency { style != CIRCLE }
    private val barHeight by NumberSetting("Height",  10.0,  4.0,  32.0, 1.0, desc = "Height of the bar (pixels → ×0.01 blocks in world)")
        .withDependency { style != CIRCLE }
    private val segmentCount by NumberSetting("Segments", 20.0, 5.0, 50.0, 1.0, desc = "Number of segment chunks")
        .withDependency { style == SEGMENTED }

    // ── Circle dimensions (3D world ring) ─────────────────────────────────
    private val circleRadius  by NumberSetting("Ring Radius",  0.8, 0.3, 3.0, 0.05, desc = "Radius of the world ring in blocks")
        .withDependency { style == CIRCLE }
    private val ringThickness by NumberSetting("Ring Thickness", 0.15, 0.03, 0.6, 0.01, desc = "How thick the ring band is (in blocks)")
        .withDependency { style == CIRCLE }
    private val ringSegments  by NumberSetting("Smoothness",   64.0, 16.0, 128.0, 8.0, desc = "Segment count for the ring")
        .withDependency { style == CIRCLE }
    private val textOffset    by NumberSetting("Text Distance", 1.1,  0.1,   3.0,  0.05, desc = "Distance below the ring center where the HP text appears (blocks)")
        .withDependency { style == CIRCLE && showText }

    // ── Colors ────────────────────────────────────────────────────────────
    private val highHpColor  by ColorSetting("High HP Color", Color(0xFF55FF55.toInt()), desc = "Color when HP is high")
    private val lowHpColor   by ColorSetting("Low HP Color",  Color(0xFFFF4444.toInt()), desc = "Color when HP is low")
    private val bgColor      by ColorSetting("Background",    Color(0, 0, 0, 0.45f),     desc = "Empty portion / ring track")
    private val borderColor  by ColorSetting("Border",        Color(0, 0, 0, 0.8f),      desc = "Outline color")
    private val textColor    by ColorSetting("Text Color",    Color(0xFFFFFFFF.toInt()), desc = "HP number color")

    // ── Options ───────────────────────────────────────────────────────────
    private val showText       by BooleanSetting("Show HP Text",         true,  desc = "Display HP numbers")
    private val textFormat     by SelectorSetting("Text Format", "Current / Max",
        listOf("Current / Max", "Current", "Percentage"), desc = "Format of the HP number"
    ).withDependency { showText }
    private val showBorder     by BooleanSetting("Show Border",          true,  desc = "Draw an outline")
        .withDependency { style != CIRCLE }
    private val onlyThirdPerson by BooleanSetting("Only in Third Person", false, desc = "Only show in F5 view")

    // ── All styles: 3D world-space rendering ──────────────────────────────
    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            if (onlyThirdPerson && mc.options.cameraType == CameraType.FIRST_PERSON) return@on
            val player = mc.player ?: return@on
            val maxHp = player.maxHealth
            if (maxHp <= 0f) return@on

            val hp        = player.health.coerceIn(0f, maxHp)
            val hpFrac    = hp / maxHp
            val fillColor = lerpColor(lowHpColor.rgba, highHpColor.rgba, hpFrac)

            val box = player.renderBoundingBox

            // Derive camera right (R) and up (U) billboard vectors
            val camera   = mc.gameRenderer.mainCamera
            val yawRad   = Math.toRadians(camera.yRot().toDouble())
            val pitchRad = Math.toRadians(camera.xRot().toDouble())
            val sinY = sin(yawRad);  val cosY = cos(yawRad)
            val sinP = sin(pitchRad); val cosP = cos(pitchRad)
            val rX = cosY;          val rY = 0.0; val rZ = sinY
            val uX = -sinY * sinP;  val uY = cosP; val uZ = cosY * sinP

            if (style == CIRCLE) {
                val cx = (box.minX + box.maxX) / 2.0
                val cy = (box.minY + box.maxY) / 2.0
                val cz = (box.minZ + box.maxZ) / 2.0

                val outerR = circleRadius
                val innerR = (outerR - ringThickness).coerceAtLeast(0.0)
                val segs   = ringSegments.toInt().coerceAtLeast(16)

                drawBillboardArc(cx, cy, cz, innerR, outerR, 0.0, PI * 2,
                    rX, rY, rZ, uX, uY, uZ, bgColor.rgba, segs)

                if (hpFrac > 0f) {
                    val half = hpFrac * PI
                    drawBillboardArc(cx, cy, cz, innerR, outerR,
                        -PI / 2 - half, -PI / 2 + half,
                        rX, rY, rZ, uX, uY, uZ, fillColor, segs)
                }
            } else {
                // 3D billboard bar — Rounded, Flat, Segmented
                val cx = (box.minX + box.maxX) / 2.0
                val cy = box.minY + BAR_WORLD_OFFSET  // just above the player's feet
                val cz = (box.minZ + box.maxZ) / 2.0

                val s  = BAR_WORLD_SCALE
                val hw = barWidth  * s / 2.0
                val hh = barHeight * s / 2.0

                // Background track
                drawBillboardRect(cx, cy, cz, -hw, -hh, hw, hh, rX, rY, rZ, uX, uY, uZ, bgColor.rgba)

                if (style == SEGMENTED) {
                    val count = segmentCount.toInt().coerceAtLeast(1)
                    val totalW = hw * 2.0
                    val gap   = 1.0 * s
                    val segW  = (totalW - gap * (count - 1)) / count
                    for (i in 0 until count) {
                        val sx     = -hw + i * (segW + gap)
                        val hpFill = ((hpFrac * count) - i).coerceIn(0f, 1f).toDouble()
                        if (hpFill > 0.0) drawBillboardRect(cx, cy, cz, sx, -hh, sx + segW * hpFill, hh, rX, rY, rZ, uX, uY, uZ, fillColor)
                    }
                } else {
                    // ROUNDED and FLAT both render as flat bars in 3D
                    if (hpFrac > 0f) {
                        drawBillboardRect(cx, cy, cz, -hw, -hh, -hw + hw * 2.0 * hpFrac, hh, rX, rY, rZ, uX, uY, uZ, fillColor)
                    }
                }

                if (showBorder) {
                    val bw = 1.0 * s
                    drawBillboardRect(cx, cy, cz, -hw - bw, hh,       hw + bw, hh + bw,  rX, rY, rZ, uX, uY, uZ, borderColor.rgba)
                    drawBillboardRect(cx, cy, cz, -hw - bw, -hh - bw, hw + bw, -hh,      rX, rY, rZ, uX, uY, uZ, borderColor.rgba)
                    drawBillboardRect(cx, cy, cz, -hw - bw, -hh,     -hw,       hh,      rX, rY, rZ, uX, uY, uZ, borderColor.rgba)
                    drawBillboardRect(cx, cy, cz,  hw,      -hh,      hw + bw,  hh,      rX, rY, rZ, uX, uY, uZ, borderColor.rgba)
                }
            }
        }

        on<RenderEvent.Last> {
            if (!enabled || !showText) return@on
            if (onlyThirdPerson && mc.options.cameraType == CameraType.FIRST_PERSON) return@on
            val player = mc.player ?: return@on
            val maxHp  = player.maxHealth
            if (maxHp <= 0f) return@on
            val hpFrac = player.health.coerceIn(0f, maxHp) / maxHp

            val box = player.renderBoundingBox
            val cam      = context.gameRenderer().mainCamera
            val yawRad   = Math.toRadians(cam.yRot().toDouble())
            val pitchRad = Math.toRadians(cam.xRot().toDouble())
            val sinY = sin(yawRad);  val cosY = cos(yawRad)
            val sinP = sin(pitchRad); val cosP = cos(pitchRad)
            val uX = -sinY * sinP;  val uY = cosP; val uZ = cosY * sinP

            val tx: Double; val ty: Double; val tz: Double

            if (style == CIRCLE) {
                val cx = (box.minX + box.maxX) / 2.0
                val cy = (box.minY + box.maxY) / 2.0
                val cz = (box.minZ + box.maxZ) / 2.0
                val off = textOffset
                tx = cx - off * uX
                ty = cy - off * uY
                tz = cz - off * uZ
            } else {
                // Above the bar (towards player body, +U direction)
                val cx = (box.minX + box.maxX) / 2.0
                val cy = box.minY + BAR_WORLD_OFFSET
                val cz = (box.minZ + box.maxZ) / 2.0
                val totalOff = barHeight * BAR_WORLD_SCALE / 2.0 + 2.0 * BAR_WORLD_SCALE
                tx = cx + totalOff * uX
                ty = cy + totalOff * uY
                tz = cz + totalOff * uZ
            }

            val camPos = cam.position()
            val ps     = context.poseStack()
            val buf    = context.bufferSource()
            val font   = mc.font
            val label  = hpLabel(hpFrac)
            val tw     = font.width(label).toFloat()

            ps.pushPose()
            ps.translate(tx - camPos.x, ty - camPos.y, tz - camPos.z)
            ps.mulPose(Axis.YP.rotationDegrees(-cam.yRot()))
            ps.mulPose(Axis.XP.rotationDegrees(cam.xRot()))
            ps.scale(-0.04f, -0.04f, 0.04f)
            font.drawInBatch(
                label, -tw / 2f, -font.lineHeight / 2f,
                textColor.rgba, true,
                ps.last().pose(), buf,
                Font.DisplayMode.SEE_THROUGH,
                0, 15728880
            )
            buf.endBatch()
            ps.popPose()
        }
    }

    // All styles render in 3D via RenderEvent — nothing to draw on the HUD
    fun renderHud(context: GuiGraphicsExtractor) {}

    // ── 3D rectangle helper (billboard plane, always-visible) ─────────────
    // x1/y1 = bottom-left, x2/y2 = top-right in billboard space
    // (x = camera-right R direction, y = camera-up U direction)
    private fun RenderEvent.Extract.drawBillboardRect(
        cx: Double, cy: Double, cz: Double,
        x1: Double, y1: Double, x2: Double, y2: Double,
        rX: Double, rY: Double, rZ: Double,
        uX: Double, uY: Double, uZ: Double,
        color: Int
    ) {
        fun pt(bx: Double, by: Double) = TriangleStripPoint(
            Vec3(cx + bx * rX + by * uX, cy + bx * rY + by * uY, cz + bx * rZ + by * uZ), color
        )
        // Triangle strip order: TL, BL, TR, BR
        consumer.triangleStrips[NO_DEPTH].add(TriangleStripData(arrayListOf(pt(x1, y2), pt(x1, y1), pt(x2, y2), pt(x2, y1))))
    }

    // ── 3D arc helper (used by Circle style) ──────────────────────────────
    private fun RenderEvent.Extract.drawBillboardArc(
        cx: Double, cy: Double, cz: Double,
        innerR: Double, outerR: Double,
        startAngle: Double, endAngle: Double,
        rX: Double, rY: Double, rZ: Double,
        uX: Double, uY: Double, uZ: Double,
        colorRgba: Int,
        segments: Int
    ) {
        if (outerR <= 0.0 || outerR <= innerR) return
        val points = ArrayList<TriangleStripPoint>(segments * 2 + 2)
        for (i in 0..segments) {
            val angle = startAngle + i.toDouble() / segments * (endAngle - startAngle)
            val c = cos(angle); val s = sin(angle)
            val bx = c * rX + s * uX
            val by = c * rY + s * uY
            val bz = c * rZ + s * uZ
            points.add(TriangleStripPoint(Vec3(cx + innerR * bx, cy + innerR * by, cz + innerR * bz), colorRgba))
            points.add(TriangleStripPoint(Vec3(cx + outerR * bx, cy + outerR * by, cz + outerR * bz), colorRgba))
        }
        consumer.triangleStrips[NO_DEPTH].add(TriangleStripData(points))
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun hpLabel(hpFrac: Float): String {
        val p = mc.player ?: return "--"
        return when (textFormat) {
            1    -> "%.1f".format(p.health)
            2    -> "%.1f%%".format(hpFrac * 100f)
            else -> "%.1f / %.0f".format(p.health, p.maxHealth)
        }
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r = lerp((c1 shr 16) and 0xFF, (c2 shr 16) and 0xFF, t)
        val g = lerp((c1 shr 8)  and 0xFF, (c2 shr 8)  and 0xFF, t)
        val b = lerp( c1         and 0xFF,  c2          and 0xFF, t)
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
}
