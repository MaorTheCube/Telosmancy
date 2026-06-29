package me.telosmancy.clickgui

import me.telosmancy.Telosmancy
import me.telosmancy.Telosmancy.mc
import me.telosmancy.clickgui.settings.ModuleButton
import me.telosmancy.clickgui.settings.impl.ColorSetting
import me.telosmancy.features.Category
import me.telosmancy.features.Module
import me.telosmancy.features.ModuleManager
import me.telosmancy.features.impl.ClickGUIModule
import me.telosmancy.profile.ProfileManager
import me.telosmancy.utils.Color
import me.telosmancy.utils.Colors
import me.telosmancy.utils.ui.HoverHandler
import me.telosmancy.utils.ui.TextInputHandler
import me.telosmancy.utils.ui.animations.EaseOutAnimation
import me.telosmancy.utils.ui.rendering.NVGPIPRenderer
import me.telosmancy.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.io.File
import kotlin.math.sign
import me.telosmancy.utils.ui.mouseX as rawMouseX
import me.telosmancy.utils.ui.mouseY as rawMouseY

object ClickGUI : Screen(Component.literal("Telosmancy")) {

    // ── Layout ────────────────────────────────────────────────────────
    const val PANEL_W  = 760f
    const val PANEL_H  = 500f
    const val SIDEBAR_W = 148f
    const val HEADER_H  = 46f
    const val CAT_H    = 44f
    const val ROW_H    = 42f
    const val ROW_PAD  = 4f
    const val CPAD     = 10f

    // ── Palette ───────────────────────────────────────────────────────
    val BG         get() = Color(11, 12, 20).rgba
    val SIDEBAR_BG get() = Color(7, 8, 15).rgba
    val ROW_NORMAL get() = Color(14, 15, 26).rgba
    val ROW_HOVER  get() = Color(19, 21, 36).rgba
    val ACTIVE_CAT get() = Color(14, 16, 28).rgba
    val TEXT_PRI   get() = Color(210, 215, 235).rgba
    val TEXT_SEC   get() = Color(78, 84, 110).rgba
    val SEP        get() = Color(255, 255, 255, 14f / 255f).rgba
    val TOGGLE_OFF get() = Color(38, 42, 64).rgba

    // Legacy refs expected by settings files
    val gray38 = Color(38, 38, 38)
    val gray26 = Color(26, 26, 26)

    // Images used by settings (ColorSetting, HUDSetting, DropdownSetting)
    val movementImage = NVGRenderer.createImage("/assets/telosmancy/MovementIcon.svg")
    val hueImage      = NVGRenderer.createImage("/assets/telosmancy/HueGradient.png")
    val chevronImage  = NVGRenderer.createImage("/assets/telosmancy/chevron.svg")

    // ── Secret unlock ─────────────────────────────────────────────────

    private val secretFlagFile by lazy { File(Telosmancy.configFile, ".secret") }
    var secretUnlocked: Boolean = false
        private set

    fun initSecretState() {
        secretUnlocked = secretFlagFile.exists()
    }

    fun unlockSecret() {
        secretUnlocked = true
        runCatching { secretFlagFile.createNewFile() }
    }

    // ── State ─────────────────────────────────────────────────────────

    // null = Profiles/Realms tab; Category.SECRET = secret "???" tab
    private var activeCategory: Category? = Category.COMBAT
    private var realmTabActive = false
    private var scrollY = 0f

    private val REALMS = listOf("Creska", "Galla", "Harvenfeld", "Inderfall", "Larpswood", "Darkon", "Holloway", "Jarnholm", "Hub-1", "Hub-2", "Hub-3")
    private var totalContentH = 0f
    private var contentAreaH = 0f
    private val openAnim = EaseOutAnimation(300)

    private var searchText = ""
    private val searchInput = TextInputHandler(
        textProvider = { searchText },
        textSetter   = { if (it.length <= 24) { searchText = it; scrollY = 0f } },
        allowEmojis  = false
    )

    private val allButtons = LinkedHashMap<Module, ModuleButton>()
    private var desc = Description("", 0f, 0f, HoverHandler(150))

    // ── Profile tab state ─────────────────────────────────────────────

    private var profileName = ""
    private val profileNameInput = TextInputHandler(
        textProvider = { profileName },
        textSetter   = { profileName = it },
        allowEmojis  = false
    )
    private var savedProfiles: List<String> = emptyList()

    // Profile layout constants (relative to content origin + CPAD - scrollY)
    private const val PROF_LABEL_H  = 22f
    private const val PROF_FIELD_H  = 34f
    private const val PROF_FIELD_GAP = 10f
    private const val PROF_SEP_ABOVE = 6f
    private const val PROF_LIST_HDR = 28f  // gap + "Saved Profiles" label
    private const val PROF_ITEM_H   = 34f

    private data class ProfileLayout(
        val fieldY: Float,
        val btnSaveY: Float,
        val btnLoadY: Float,
        val btnDeleteY: Float,
        val listStartY: Float
    )

    private fun profileLayout(cy: Float): ProfileLayout {
        val base = cy + CPAD - scrollY
        val fieldY = base + PROF_LABEL_H
        val btnSaveY   = fieldY + PROF_FIELD_H + PROF_FIELD_GAP
        val btnLoadY   = btnSaveY   + ROW_H + ROW_PAD
        val btnDeleteY = btnLoadY   + ROW_H + ROW_PAD
        val listStartY = btnDeleteY + ROW_H + PROF_SEP_ABOVE + 1f + PROF_LIST_HDR
        return ProfileLayout(fieldY, btnSaveY, btnLoadY, btnDeleteY, listStartY)
    }

    fun setDescription(text: String, x: Float, y: Float, hoverHandler: HoverHandler) {
        desc.text = text; desc.x = x; desc.y = y; desc.hoverHandler = hoverHandler
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun init() {
        openAnim.start()
        initSecretState()
        if (allButtons.isEmpty()) {
            for ((_, modules) in ModuleManager.modulesByCategory)
                for (m in modules) allButtons[m] = ModuleButton(m)
        }
        scrollY = 0f
        realmTabActive = false
        for (btn in allButtons.values) btn.extended = false
        super.init()
    }

    fun openOnRealmsTab() {
        activeCategory = null
        realmTabActive = true
        scrollY = 0f
    }

    override fun onClose() {
        for (btn in allButtons.values) {
            if (!btn.extended) continue
            for (s in btn.representableSettings) {
                if (s is ColorSetting) s.section = null
                s.listening = false
            }
        }
        ModuleManager.saveConfigurations()
        super.onClose()
    }

    override fun isPauseScreen() = false

    // ── Render ────────────────────────────────────────────────────────

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, dt: Float) {
        NVGPIPRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight()) {
            val gs = ClickGUIModule.getStandardGuiScale()
            NVGRenderer.scale(gs, gs)
            val mx = rawMouseX / gs
            val my = rawMouseY / gs

            val px = (mc.window.screenWidth / gs - PANEL_W) / 2f
            val py = (mc.window.screenHeight / gs - PANEL_H) / 2f

            if (openAnim.isAnimating()) {
                val t = openAnim.get(0f, 1f)
                NVGRenderer.globalAlpha(t)
                val cx = px + PANEL_W / 2f
                val cy = py + PANEL_H / 2f
                NVGRenderer.push()
                NVGRenderer.translate(cx, cy)
                NVGRenderer.scale(0.93f + 0.07f * t, 0.93f + 0.07f * t)
                NVGRenderer.translate(-cx, -cy)
            }

            drawPanel(px, py, mx, my)

            if (openAnim.isAnimating()) { NVGRenderer.pop(); NVGRenderer.globalAlpha(1f) }

            desc.render()
        }
        super.extractRenderState(ctx, mouseX, mouseY, dt)
    }

    private fun drawPanel(px: Float, py: Float, mx: Float, my: Float) {
        NVGRenderer.dropShadow(px, py, PANEL_W, PANEL_H, 24f, 6f, 12f)
        NVGRenderer.rect(px, py, PANEL_W, PANEL_H, BG, 10f)

        drawHeader(px, py, mx, my)
        NVGRenderer.rect(px, py + HEADER_H, PANEL_W, 1f, SEP)

        val sy = py + HEADER_H + 1f
        val sideH = PANEL_H - HEADER_H - 1f

        NVGRenderer.pushScissor(px, sy, SIDEBAR_W, sideH)
        drawSidebar(px, sy, sideH, mx, my)
        NVGRenderer.popScissor()

        NVGRenderer.rect(px + SIDEBAR_W, sy, 1f, sideH, SEP)

        val cx = px + SIDEBAR_W + 1f
        val cw = PANEL_W - SIDEBAR_W - 1f
        contentAreaH = sideH

        NVGRenderer.pushScissor(cx, sy, cw, sideH)
        drawContent(cx, sy, cw, sideH, mx, my)
        NVGRenderer.popScissor()

        if (totalContentH > sideH) {
            val thumbRatio = (sideH / totalContentH).coerceAtMost(1f)
            val scrollRatio = scrollY / (totalContentH - sideH).coerceAtLeast(1f)
            drawScrollbar(px + PANEL_W - 5f, sy, 4f, sideH, thumbRatio, scrollRatio)
        }
    }

    private fun drawHeader(px: Float, py: Float, mx: Float, my: Float) {
        NVGRenderer.rect(px, py, PANEL_W, HEADER_H, SIDEBAR_BG, 10f)
        NVGRenderer.rect(px, py + 20f, PANEL_W, HEADER_H - 20f, SIDEBAR_BG)

        NVGRenderer.text("TELOSMANCY", px + 18f, py + HEADER_H / 2f - 8f, 14f, ClickGUIModule.clickGUIColor.rgba, NVGRenderer.defaultFont)

        val sw = 220f; val sh = 26f
        val sx = px + PANEL_W / 2f - sw / 2f
        val sy2 = py + HEADER_H / 2f - sh / 2f
        NVGRenderer.rect(sx, sy2, sw, sh, Color(20, 22, 40).rgba, 6f)
        NVGRenderer.hollowRect(sx, sy2, sw, sh, 1f, SEP, 6f)

        if (searchText.isEmpty()) {
            val pw = NVGRenderer.textWidth("Search modules...", 13f, NVGRenderer.defaultFont)
            NVGRenderer.text("Search modules...", sx + sw / 2f - pw / 2f, sy2 + sh / 2f - 7f, 13f, TEXT_SEC, NVGRenderer.defaultFont)
        }
        searchInput.x = sx + 6f
        searchInput.y = sy2 + sh / 2f - 7f
        searchInput.width = sw - 12f
        searchInput.height = 14f
        searchInput.draw(mx, my)

        val overClose = mx >= px + PANEL_W - 32f && mx <= px + PANEL_W - 14f && my >= py + 8f && my <= py + 38f
        NVGRenderer.text("×", px + PANEL_W - 26f, py + HEADER_H / 2f - 9f, 18f, if (overClose) Colors.WHITE.rgba else TEXT_SEC, NVGRenderer.defaultFont)
    }

    private fun drawSidebar(sx: Float, sy: Float, sideH: Float, mx: Float, my: Float) {
        NVGRenderer.rect(sx, sy, SIDEBAR_W, sideH, SIDEBAR_BG)

        // Regular category tabs
        Category.categories.values.forEachIndexed { i, cat ->
            val ty = sy + i * CAT_H
            val isActive = cat == activeCategory
            val isHov = mx >= sx && mx <= sx + SIDEBAR_W && my >= ty && my <= ty + CAT_H
            when {
                isActive -> {
                    NVGRenderer.rect(sx, ty, SIDEBAR_W, CAT_H, ACTIVE_CAT)
                    NVGRenderer.rect(sx, ty, 3f, CAT_H, ClickGUIModule.clickGUIColor.rgba, 1.5f)
                }
                isHov -> NVGRenderer.rect(sx, ty, SIDEBAR_W, CAT_H, Color(255, 255, 255, 8f / 255f).rgba)
            }
            if (i > 0) NVGRenderer.rect(sx + 14f, ty, SIDEBAR_W - 28f, 1f, SEP)
            val tw = NVGRenderer.textWidth(cat.name, 13f, NVGRenderer.defaultFont)
            NVGRenderer.text(cat.name, sx + SIDEBAR_W / 2f - tw / 2f, ty + CAT_H / 2f - 7f, 13f, if (isActive) Colors.WHITE.rgba else TEXT_SEC, NVGRenderer.defaultFont)
        }

        // PROFILES tab — pinned below categories with a separator
        val profY = sy + Category.categories.size * CAT_H
        NVGRenderer.rect(sx + 14f, profY, SIDEBAR_W - 28f, 1f, SEP)

        val isProfileActive = activeCategory == null && !realmTabActive
        val isProfileHov = mx >= sx && mx <= sx + SIDEBAR_W && my >= profY && my <= profY + CAT_H
        when {
            isProfileActive -> {
                NVGRenderer.rect(sx, profY, SIDEBAR_W, CAT_H, ACTIVE_CAT)
                NVGRenderer.rect(sx, profY, 3f, CAT_H, ClickGUIModule.clickGUIColor.rgba, 1.5f)
            }
            isProfileHov -> NVGRenderer.rect(sx, profY, SIDEBAR_W, CAT_H, Color(255, 255, 255, 8f / 255f).rgba)
        }
        val ptw = NVGRenderer.textWidth("PROFILES", 13f, NVGRenderer.defaultFont)
        NVGRenderer.text("PROFILES", sx + SIDEBAR_W / 2f - ptw / 2f, profY + CAT_H / 2f - 7f, 13f, if (isProfileActive) Colors.WHITE.rgba else TEXT_SEC, NVGRenderer.defaultFont)

        // REALMS tab
        val realmY = profY + CAT_H
        NVGRenderer.rect(sx + 14f, realmY, SIDEBAR_W - 28f, 1f, SEP)
        val isRealmActive = activeCategory == null && realmTabActive
        val isRealmHov = mx >= sx && mx <= sx + SIDEBAR_W && my >= realmY && my <= realmY + CAT_H
        when {
            isRealmActive -> {
                NVGRenderer.rect(sx, realmY, SIDEBAR_W, CAT_H, ACTIVE_CAT)
                NVGRenderer.rect(sx, realmY, 3f, CAT_H, ClickGUIModule.clickGUIColor.rgba, 1.5f)
            }
            isRealmHov -> NVGRenderer.rect(sx, realmY, SIDEBAR_W, CAT_H, Color(255, 255, 255, 8f / 255f).rgba)
        }
        val rtw = NVGRenderer.textWidth("REALMS", 13f, NVGRenderer.defaultFont)
        NVGRenderer.text("REALMS", sx + SIDEBAR_W / 2f - rtw / 2f, realmY + CAT_H / 2f - 7f, 13f, if (isRealmActive) Colors.WHITE.rgba else TEXT_SEC, NVGRenderer.defaultFont)

        // "???" secret tab — only shown after /FreeIsrael is typed
        if (secretUnlocked) {
            val secY = realmY + CAT_H
            NVGRenderer.rect(sx + 14f, secY, SIDEBAR_W - 28f, 1f, SEP)
            val isSecretActive = activeCategory == Category.SECRET
            val isSecretHov = mx >= sx && mx <= sx + SIDEBAR_W && my >= secY && my <= secY + CAT_H
            when {
                isSecretActive -> {
                    NVGRenderer.rect(sx, secY, SIDEBAR_W, CAT_H, ACTIVE_CAT)
                    NVGRenderer.rect(sx, secY, 3f, CAT_H, ClickGUIModule.clickGUIColor.rgba, 1.5f)
                }
                isSecretHov -> NVGRenderer.rect(sx, secY, SIDEBAR_W, CAT_H, Color(255, 255, 255, 8f / 255f).rgba)
            }
            val stw = NVGRenderer.textWidth("???", 13f, NVGRenderer.defaultFont)
            NVGRenderer.text("???", sx + SIDEBAR_W / 2f - stw / 2f, secY + CAT_H / 2f - 7f, 13f,
                if (isSecretActive) ClickGUIModule.clickGUIColor.rgba else TEXT_SEC, NVGRenderer.defaultFont)
        }
    }

    private fun drawContent(cx: Float, cy: Float, cw: Float, ch: Float, mx: Float, my: Float) {
        when {
            activeCategory == null && realmTabActive -> drawRealmsTab(cx, cy, cw, ch, mx, my)
            activeCategory == null -> drawProfilesTab(cx, cy, cw, ch, mx, my)
            else -> drawModules(cx, cy, cw, ch, mx, my)
        }
    }

    private fun drawModules(cx: Float, cy: Float, cw: Float, ch: Float, mx: Float, my: Float) {
        val buttons = visibleButtons()
        var drawY = CPAD - scrollY
        var absY = CPAD
        for (btn in buttons) {
            val h = btn.getHeight()
            if (drawY + h >= 0f && drawY <= ch)
                btn.draw(cx + CPAD, cy + drawY, cw - CPAD * 2f, mx, my)
            drawY += h + ROW_PAD
            absY += h + ROW_PAD
        }
        totalContentH = absY
    }

    private fun drawProfilesTab(cx: Float, cy: Float, cw: Float, ch: Float, mx: Float, my: Float) {
        val pad = CPAD
        val fw = cw - pad * 2f
        val layout = profileLayout(cy)

        // ── "Profile Name" label ──────────────────────────────────────
        NVGRenderer.text("Profile Name", cx + pad, cy + CPAD - scrollY + 5f, 12f, TEXT_SEC, NVGRenderer.defaultFont)

        // ── Text input field ──────────────────────────────────────────
        NVGRenderer.rect(cx + pad, layout.fieldY, fw, PROF_FIELD_H, ROW_NORMAL, 6f)
        NVGRenderer.hollowRect(cx + pad, layout.fieldY, fw, PROF_FIELD_H, 1f, SEP, 6f)
        if (profileName.isEmpty()) {
            val ph = "Enter a profile name..."
            val phW = NVGRenderer.textWidth(ph, 13f, NVGRenderer.defaultFont)
            NVGRenderer.text(ph, cx + pad + fw / 2f - phW / 2f, layout.fieldY + PROF_FIELD_H / 2f - 7f, 13f, TEXT_SEC, NVGRenderer.defaultFont)
        }
        profileNameInput.x = cx + pad + 10f
        profileNameInput.y = layout.fieldY + PROF_FIELD_H / 2f - 7f
        profileNameInput.width = fw - 20f
        profileNameInput.height = 14f
        profileNameInput.draw(mx, my)

        // ── Action buttons ────────────────────────────────────────────
        drawProfileButton(cx + pad, layout.btnSaveY,   fw, "Save Profile",   mx, my)
        drawProfileButton(cx + pad, layout.btnLoadY,   fw, "Load Profile",   mx, my)
        drawProfileButton(cx + pad, layout.btnDeleteY, fw, "Delete Profile", mx, my)

        // ── Separator ─────────────────────────────────────────────────
        val sepY = layout.btnDeleteY + ROW_H + PROF_SEP_ABOVE
        NVGRenderer.rect(cx + pad, sepY, fw, 1f, SEP)

        // ── Saved profiles list ───────────────────────────────────────
        NVGRenderer.text("Saved Profiles", cx + pad, sepY + 8f, 12f, TEXT_SEC, NVGRenderer.defaultFont)

        if (savedProfiles.isEmpty()) {
            NVGRenderer.text("No profiles yet — save one above.", cx + pad, layout.listStartY, 12f, TEXT_SEC, NVGRenderer.defaultFont)
        } else {
            var itemY = layout.listStartY
            for (profile in savedProfiles) {
                val isSelected = profileName == profile
                val hov = mx >= cx + pad && mx <= cx + pad + fw && my >= itemY && my <= itemY + PROF_ITEM_H
                NVGRenderer.rect(cx + pad, itemY, fw, PROF_ITEM_H, if (hov) ROW_HOVER else ROW_NORMAL, 6f)
                NVGRenderer.rect(cx + pad, itemY, 3f, PROF_ITEM_H,
                    if (isSelected) ClickGUIModule.clickGUIColor.rgba else Color(255, 255, 255, 30f / 255f).rgba, 1.5f)
                NVGRenderer.text(profile, cx + pad + 14f, itemY + PROF_ITEM_H / 2f - 7f, 13f, if (isSelected) Colors.WHITE.rgba else TEXT_PRI, NVGRenderer.defaultFont)
                itemY += PROF_ITEM_H + ROW_PAD
            }
        }

        val listH = savedProfiles.size * (PROF_ITEM_H + ROW_PAD)
        totalContentH = (layout.listStartY - cy) + scrollY + listH + CPAD
    }

    private fun drawRealmsTab(cx: Float, cy: Float, cw: Float, ch: Float, mx: Float, my: Float) {
        val pad = CPAD
        val fw = cw - pad * 2f
        var drawY = pad - scrollY
        var absY = pad

        for (realm in REALMS) {
            if (drawY + ROW_H >= 0f && drawY <= ch) {
                val hov = mx >= cx + pad && mx <= cx + pad + fw && my >= cy + drawY && my <= cy + drawY + ROW_H
                NVGRenderer.rect(cx + pad, cy + drawY, fw, ROW_H, if (hov) ROW_HOVER else ROW_NORMAL, 6f)
                NVGRenderer.text(realm, cx + pad + 14f, cy + drawY + ROW_H / 2f - 8f, 14f, TEXT_PRI, NVGRenderer.defaultFont)
            }
            drawY += ROW_H + ROW_PAD
            absY += ROW_H + ROW_PAD
        }

        totalContentH = absY
    }

    private fun handleRealmClick(cx: Float, cy: Float, cw: Float, mx: Float, my: Float, event: MouseButtonEvent) {
        if (event.button() != 0) return
        val pad = CPAD
        val fw = cw - pad * 2f
        var drawY = pad - scrollY
        for (realm in REALMS) {
            if (mx >= cx + pad && mx <= cx + pad + fw && my >= cy + drawY && my <= cy + drawY + ROW_H) {
                mc.player?.connection?.sendCommand("joinq $realm")
                mc.setScreen(null)
                return
            }
            drawY += ROW_H + ROW_PAD
        }
    }

    private fun drawProfileButton(x: Float, y: Float, w: Float, label: String, mx: Float, my: Float) {
        val hov = mx >= x && mx <= x + w && my >= y && my <= y + ROW_H
        NVGRenderer.rect(x, y, w, ROW_H, if (hov) ROW_HOVER else ROW_NORMAL, 6f)
        NVGRenderer.text(label, x + 14f, y + ROW_H / 2f - 7f, 13f, TEXT_PRI, NVGRenderer.defaultFont)
    }

    private fun visibleButtons(): List<ModuleButton> {
        val cat = activeCategory ?: return emptyList()
        return if (searchText.isNotEmpty()) {
            Category.categories.values.flatMap { c ->
                (ModuleManager.modulesByCategory[c] ?: emptyList())
                    .filter { it.name.contains(searchText, ignoreCase = true) }
                    .mapNotNull { allButtons[it] }
            }
        } else {
            (ModuleManager.modulesByCategory[cat] ?: emptyList()).mapNotNull { allButtons[it] }
        }
    }

    private fun drawScrollbar(x: Float, y: Float, w: Float, h: Float, thumbRatio: Float, scrollRatio: Float) {
        NVGRenderer.rect(x, y, w, h, Color(255, 255, 255, 20f / 255f).rgba, w / 2f)
        val thumbH = (h * thumbRatio).coerceAtLeast(30f)
        val thumbY = y + (h - thumbH) * scrollRatio.coerceIn(0f, 1f)
        NVGRenderer.rect(x, thumbY, w, thumbH, Color(255, 255, 255, 60f / 255f).rgba, w / 2f)
    }

    // ── Input ─────────────────────────────────────────────────────────

    override fun mouseScrolled(mxd: Double, myd: Double, hAmt: Double, vAmt: Double): Boolean {
        val maxS = (totalContentH - contentAreaH).coerceAtLeast(0f)
        scrollY = (scrollY - (vAmt.sign * 24).toFloat()).coerceIn(0f, maxS)
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, bl: Boolean): Boolean {
        val gs = ClickGUIModule.getStandardGuiScale()
        val mx = rawMouseX / gs
        val my = rawMouseY / gs
        val px = (mc.window.screenWidth / gs - PANEL_W) / 2f
        val py = (mc.window.screenHeight / gs - PANEL_H) / 2f

        // Close button
        if (event.button() == 0 && mx >= px + PANEL_W - 32f && mx <= px + PANEL_W - 14f && my >= py + 8f && my <= py + 38f) {
            mc.setScreen(null); return true
        }

        searchInput.mouseClicked(mx, my, event)

        val sy = py + HEADER_H + 1f
        val cx = px + SIDEBAR_W + 1f
        val cy = py + HEADER_H + 1f
        val cw = PANEL_W - SIDEBAR_W - 1f
        val ch = PANEL_H - HEADER_H - 1f

        // Sidebar clicks
        if (event.button() == 0 && mx >= px && mx <= px + SIDEBAR_W) {
            Category.categories.values.forEachIndexed { i, cat ->
                val ty = sy + i * CAT_H
                if (my >= ty && my <= ty + CAT_H) {
                    if (activeCategory != cat) { activeCategory = cat; scrollY = 0f }
                    return true
                }
            }
            val profY = sy + Category.categories.size * CAT_H
            if (my >= profY && my <= profY + CAT_H) {
                if (activeCategory != null || realmTabActive) {
                    activeCategory = null
                    realmTabActive = false
                    scrollY = 0f
                    savedProfiles = ProfileManager.list()
                }
                return true
            }
            val realmY = profY + CAT_H
            if (my >= realmY && my <= realmY + CAT_H) {
                if (activeCategory != null || !realmTabActive) {
                    activeCategory = null
                    realmTabActive = true
                    scrollY = 0f
                }
                return true
            }
            if (secretUnlocked) {
                val secY = realmY + CAT_H
                if (my >= secY && my <= secY + CAT_H) {
                    if (activeCategory != Category.SECRET) { activeCategory = Category.SECRET; scrollY = 0f; realmTabActive = false }
                    return true
                }
            }
        }

        // Content area clicks
        if (mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch) {
            when {
                activeCategory == null && realmTabActive -> handleRealmClick(cx, cy, cw, mx, my, event)
                activeCategory == null -> handleProfileClick(cx, cy, cw, mx, my, event)
                else -> {
                    val buttons = visibleButtons()
                    var drawY = CPAD - scrollY
                    for (btn in buttons) {
                        val h = btn.getHeight()
                        if (btn.mouseClicked(cx + CPAD, cy + drawY, cw - CPAD * 2f, mx, my, event)) return true
                        drawY += h + ROW_PAD
                    }
                }
            }
        }
        return super.mouseClicked(event, bl)
    }

    private fun handleProfileClick(cx: Float, cy: Float, cw: Float, mx: Float, my: Float, event: MouseButtonEvent) {
        if (event.button() != 0) return
        val pad = CPAD
        val fw = cw - pad * 2f
        val layout = profileLayout(cy)

        profileNameInput.mouseClicked(mx, my, event)

        val name = profileName.trim()

        // Save
        if (mx >= cx + pad && mx <= cx + pad + fw && my >= layout.btnSaveY && my <= layout.btnSaveY + ROW_H) {
            if (name.isNotBlank()) {
                ProfileManager.save(name)
                savedProfiles = ProfileManager.list()
            }
            return
        }
        // Load
        if (mx >= cx + pad && mx <= cx + pad + fw && my >= layout.btnLoadY && my <= layout.btnLoadY + ROW_H) {
            if (name.isNotBlank()) ProfileManager.load(name)
            return
        }
        // Delete
        if (mx >= cx + pad && mx <= cx + pad + fw && my >= layout.btnDeleteY && my <= layout.btnDeleteY + ROW_H) {
            if (name.isNotBlank()) {
                ProfileManager.delete(name)
                savedProfiles = ProfileManager.list()
                if (profileName == name) profileName = ""
            }
            return
        }

        // Profile list items — click to select name
        var itemY = layout.listStartY
        for (profile in savedProfiles) {
            if (mx >= cx + pad && mx <= cx + pad + fw && my >= itemY && my <= itemY + PROF_ITEM_H) {
                profileName = profile
                return
            }
            itemY += PROF_ITEM_H + ROW_PAD
        }
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        searchInput.mouseReleased()
        profileNameInput.mouseReleased()
        allButtons.values.forEach { it.mouseReleased(event) }
        return super.mouseReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (activeCategory == null) {
            if (profileNameInput.keyTyped(event)) return true
        } else {
            if (searchInput.keyTyped(event)) return true
            allButtons.values.forEach { it.keyTyped(event) }
        }
        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        // Route to settings first — a keybind in listening mode must get ESC before the close handler
        if (activeCategory == null) {
            if (profileNameInput.keyPressed(event)) return true
        } else {
            if (searchInput.keyPressed(event)) return true
            if (allButtons.values.any { it.keyPressed(event) }) return true
        }
        if (event.key == GLFW.GLFW_KEY_ESCAPE) { mc.setScreen(null); return true }
        return super.keyPressed(event)
    }

    // ── Description tooltip ───────────────────────────────────────────

    data class Description(var text: String, var x: Float, var y: Float, var hoverHandler: HoverHandler) {
        fun render() {
            if (text.isEmpty() || hoverHandler.percent() < 100) return
            val area = NVGRenderer.wrappedTextBounds(text, 300f, 16f, NVGRenderer.defaultFont)
            val w = area[2] - area[0] + 16f
            val h = area[3] - area[1] + 16f
            NVGRenderer.rect(x, y, w, h, gray38.rgba, 5f)
            NVGRenderer.hollowRect(x, y, w, h, 1.5f, ClickGUIModule.clickGUIColor.rgba, 5f)
            NVGRenderer.drawWrappedString(text, x + 8f, y + 8f, 300f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        }
    }
}
