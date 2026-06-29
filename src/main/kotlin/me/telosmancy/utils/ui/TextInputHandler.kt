package me.telosmancy.utils.ui

import me.telosmancy.utils.Color
import me.telosmancy.utils.Colors
import me.telosmancy.utils.emoji.EmojiShortcodes
import me.telosmancy.utils.ui.rendering.NVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.StringUtil
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * Text input handler for text fields in the ClickGUI.
 */
class TextInputHandler(
    private val textProvider: () -> String,
    private val textSetter: (String) -> Unit,
    private val allowEmojis: Boolean = true
) {
    private inline val text: String get() = textProvider()
    
    var x = 0f
    var y = 0f
    var width = 0f
    var height = 18f
    
    private var caret = text.length
        set(value) {
            val safeLen = text.length
            val clamped = value.coerceIn(0, safeLen)
            if (field == clamped) return
            field = clamped
            caretBlinkTime = System.currentTimeMillis()
        }
    
    private var selection = text.length
        set(value) {
            val safeLen = text.length
            field = value.coerceIn(0, safeLen)
        }
    
    private var selectionWidth = 0f
    private var textOffset = 0f
    private var caretX = 0f
    
    private var caretBlinkTime = System.currentTimeMillis()
    private var lastClickTime = 0L
    private var listening = false
    private var dragging = false
    private var clickCount = 1
    
    private val history = mutableListOf<String>()
    private var historyIndex = -1
    private var lastSavedText = ""

    init {
        saveState()
    }

    private var previousMousePos = 0f to 0f

    fun draw(mouseX: Float, mouseY: Float) {
        if (previousMousePos != mouseX to mouseY) mouseDragged(mouseX)
        previousMousePos = mouseX to mouseY

        NVGRenderer.pushScissor(x, y, width, height)
        if (selectionWidth != 0f) NVGRenderer.rect(
            x + caretX + 4f,
            y,
            selectionWidth,
            height,
            Color(138, 0, 0).rgba, // Telosmancy red: 0xFF8A0000
            4f
        )
        NVGRenderer.popScissor()

        if (listening) {
            val time = System.currentTimeMillis()
            if (time - caretBlinkTime < 500)
                NVGRenderer.line(
                    x + caretX + 4f - textOffset,
                    y,
                    x + caretX + 4f - textOffset,
                    y + height,
                    2f,
                    Colors.WHITE.rgba
                )
            else if (time - caretBlinkTime > 1000)
                caretBlinkTime = System.currentTimeMillis()
        }
        NVGRenderer.pushScissor(x, y, width, height)

        NVGRenderer.text(text, x + 4f - textOffset, y + 2f, height - 2, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        NVGRenderer.popScissor()
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (!isAreaHovered(x, y, width, height)) {
            resetState()
            return false
        }
        if (click.button() != 0) return false

        listening = true
        dragging = true

        val current = System.currentTimeMillis()
        if (current - lastClickTime < 200) clickCount++ else clickCount = 1
        lastClickTime = current

        when (clickCount) {
            1 -> {
                caretFromMouse(mouseX)
                clearSelection()
            }

            2 -> selectWord()
            3 -> selectAll()
            4 -> clickCount = 0
        }
        return true
    }

    fun mouseReleased() {
        dragging = false
    }

    private fun mouseDragged(mouseX: Float) {
        if (dragging) caretFromMouse(mouseX)
    }
    
    // Dynamically traverses code points to prevent crashing when slicing Surrogate Pairs
    private fun getPrevCaret(current: Int): Int {
        if (current <= 0 || current > text.length) return 0
        return current - Character.charCount(text.codePointBefore(current))
    }
    
    private fun getNextCaret(current: Int): Int {
        if (current < 0 || current >= text.length) return text.length
        return current + Character.charCount(text.codePointAt(current))
    }
    
    fun keyPressed(input: KeyEvent): Boolean {
        if (!listening) return false
        
        // Always coerce before processing any operation in case external states shifted the string
        selection = selection.coerceIn(0, text.length)
        caret = caret.coerceIn(0, text.length)
        
        val returnValue = when (input.key) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (selection != caret) {
                    deleteSelection()
                } else if (input.hasControlDown()) {
                    val previousSpace = getPreviousSpace()
                    textSetter(text.removeRangeSafe(previousSpace, caret))
                    caret -= if (caret > previousSpace) caret - previousSpace else 0
                } else if (caret > 0) {
                    val prev = getPrevCaret(caret)
                    textSetter(text.removeRangeSafe(prev, caret))
                    caret = prev
                }
                clearSelection()
                selection != caret || input.hasControlDown() || caret != 0
            }

            GLFW.GLFW_KEY_DELETE -> {
                if (selection != caret) {
                    deleteSelection()
                } else if (input.hasControlDown()) {
                    val nextSpace = getNextSpace()
                    textSetter(text.removeRangeSafe(caret, nextSpace))
                    caret = if (caret < nextSpace) caret else nextSpace
                } else if (caret < text.length) {
                    val next = getNextCaret(caret)
                    textSetter(text.removeRangeSafe(caret, next))
                }
                clearSelection()
                selection != caret || input.hasControlDown() || caret != text.length
            }

            GLFW.GLFW_KEY_RIGHT -> {
                if (caret < text.length) {
                    caret = if (input.hasControlDown()) getNextSpace() else getNextCaret(caret)
                    if (!input.hasShiftDown()) selection = caret
                    true
                } else false
            }

            GLFW.GLFW_KEY_LEFT -> {
                if (caret > 0) {
                    caret = if (input.hasControlDown()) getPreviousSpace() else getPrevCaret(caret)
                    if (!input.hasShiftDown()) selection = caret
                    true
                } else false
            }

            GLFW.GLFW_KEY_HOME -> {
                caret = 0
                if (!input.hasShiftDown()) selection = caret
                true
            }

            GLFW.GLFW_KEY_END -> {
                caret = text.length
                if (!input.hasShiftDown()) selection = caret
                true
            }

            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> {
                listening = false
                true
            }

            else -> {
                if (input.hasControlDown() && !input.hasShiftDown()) {
                    when (input.key) {
                        GLFW.GLFW_KEY_V -> {
                            Minecraft.getInstance().keyboardHandler.clipboard?.let { insert(it) }
                            true
                        }

                        GLFW.GLFW_KEY_C -> {
                            if (caret != selection) {
                                Minecraft.getInstance().keyboardHandler.clipboard = text.substringSafe(caret, selection)
                                true
                            } else false
                        }

                        GLFW.GLFW_KEY_X -> {
                            if (caret != selection) {
                                Minecraft.getInstance().keyboardHandler.clipboard = text.substringSafe(caret, selection)
                                deleteSelection()
                                true
                            } else false
                        }

                        GLFW.GLFW_KEY_A -> {
                            selection = 0
                            caret = text.length
                            true
                        }

                        GLFW.GLFW_KEY_W -> {
                            selectWord()
                            true
                        }

                        GLFW.GLFW_KEY_Z -> {
                            undo()
                            true
                        }

                        GLFW.GLFW_KEY_Y -> {
                            redo()
                            true
                        }

                        else -> false
                    }
                } else false
            }
        }
        updateCaretPosition()
        return returnValue
    }

    fun keyTyped(input: CharacterEvent): Boolean {
        if (!listening) return false
 
        val charStr = input.codepointAsString()
        val cp = charStr.codePointAt(0)
        
        val isEmoji = cp > 0xFFFF || (cp in 0x2600..0x27BF) || (cp in 0x2300..0x23FF) || cp == 0x200D || (cp in 0xFE0E..0xFE0F)
        
        if (isEmoji && !allowEmojis) return true
        
        val toInsert = if (isEmoji && allowEmojis) charStr else StringUtil.filterText(charStr)
        if (toInsert.isNotEmpty()) {
            insert(toInsert)
        }
        return true
    }
    
    private fun insert(string: String) {
        var newText = text
        var newCaret = caret.coerceIn(0, text.length)
        val safeSelection = selection.coerceIn(0, text.length)
        
        if (newCaret != safeSelection) {
            newText = newText.removeRangeSafe(newCaret, safeSelection)
            newCaret = min(safeSelection, newCaret)
        }
        
        val textBefore = newText.substringSafe(0, newCaret)
        val textAfter = newText.substringSafe(newCaret, newText.length)
        
        newText = textBefore + string + textAfter
        newCaret += string.length
        
        setTextAndProcess(newText, newCaret)
        
        clearSelection()
        updateCaretPosition()
        saveState()
    }
    
    private fun setTextAndProcess(newText: String, newCaret: Int) {
        if (!allowEmojis) {
            val sb = StringBuilder()
            var adjustedCaret = newCaret
            var i = 0
            while (i < newText.length) {
                val cp = newText.codePointAt(i)
                val charCount = Character.charCount(cp)
                val isEmoji = cp > 0xFFFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF || cp == 0x200D || cp == 0xFE0E || cp == 0xFE0F || cp == 0x20E3 || cp in 0xF400..0xF498
                
                if (!isEmoji) sb.appendCodePoint(cp)
                else if (i < newCaret) adjustedCaret -= charCount
                i += charCount
            }
            textSetter(sb.toString())
            caret = adjustedCaret.coerceIn(0, sb.length)
            return
        }
        
        fun matchNativeEmoji(text: String, startIndex: Int, nativeKey: String): Int {
            var tI = startIndex
            var nI = 0
            while (nI < nativeKey.length && tI < text.length) {
                val tChar = text[tI]
                val nChar = nativeKey[nI]
                
                if (tChar == nChar) { tI++; nI++ }
                else if (tChar == '\uFE0F') tI++
                else if (nChar == '\uFE0F') nI++
                else return -1
            }
            while (nI < nativeKey.length && nativeKey[nI] == '\uFE0F') nI++
            return if (nI == nativeKey.length) tI - startIndex else -1
        }
        
        val sbNorm = java.lang.StringBuilder()
        var tempCaret = newCaret
        var j = 0
        while (j < newText.length) {
            val cp = newText.codePointAt(j)
            val charCount = Character.charCount(cp)
            
            if (cp in 0xF400..0xF498) {
                val cStr = String(Character.toChars(cp))
                var foundNative: String? = null
                for ((native, shortcode) in EmojiShortcodes.nativeToShortcode) {
                    if (EmojiShortcodes.mappings[shortcode] == cStr) {
                        foundNative = native
                        break
                    }
                }
                if (foundNative != null) {
                    sbNorm.append(foundNative)
                    if (j < newCaret) tempCaret += foundNative.length - charCount
                } else {
                    sbNorm.appendCodePoint(cp)
                }
            } else if (cp in 0x1525E..0x152AB) {
                // If a Server Emoji code point is somehow injected/pasted into the box,
                // revert it BACK to its shortcode so it remains readable instead of drawing a missing-glyph box.
                val cStr = String(Character.toChars(cp))
                val shortcode = EmojiShortcodes.reverseMappings[cStr]
                if (shortcode != null) {
                    sbNorm.append(shortcode)
                    if (j < newCaret) tempCaret += shortcode.length - charCount
                } else {
                    sbNorm.appendCodePoint(cp)
                }
            } else {
                sbNorm.appendCodePoint(cp)
            }
            j += charCount
        }
        var processedText = sbNorm.toString()
        var processedCaret = tempCaret
        
        val sb = java.lang.StringBuilder()
        var i = 0
        // Access the newly cached map directly instead of sorting it every keystroke
        val sortedNatives = EmojiShortcodes.sortedNatives
        
        while (i < processedText.length) {
            var matchedNative: String? = null
            var matchLength = 0
            
            for (native in sortedNatives) {
                val len = matchNativeEmoji(processedText, i, native)
                if (len > 0) {
                    matchedNative = native
                    matchLength = len
                    break
                }
            }
            
            if (matchedNative != null) {
                val shortcode = EmojiShortcodes.nativeToShortcode[matchedNative]
                val modEmoji = EmojiShortcodes.mappings[shortcode]
                
                if (modEmoji != null) {
                    sb.append(modEmoji)
                    if (i < processedCaret) {
                        if (processedCaret >= i + matchLength) processedCaret += modEmoji.length - matchLength
                        else processedCaret = sb.length
                    }
                } else {
                    sb.append(processedText.substring(i, i + matchLength))
                }
                i += matchLength
            } else {
                val cp = processedText.codePointAt(i)
                val isMod = cp in 0x1F3FB..0x1F3FF || cp == 0xFE0E || cp == 0xFE0F || cp == 0x20E3
                val isReg = cp in 0x1F1E6..0x1F1FF
                
                // Allow our UI Mod emojis
                val isModEmoji = cp in 0xF400..0xF498
                val isEmojiChar = cp > 0xFFFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF || cp == 0x200D || isMod || isReg
                
                if (isModEmoji) {
                    sb.appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
                else if (isEmojiChar) {
                    var sequenceLength = 0
                    var tempI = i
                    var hasBase = false
                    var lastWasJoiner = false
                    var regionalCount = 0
                    
                    while (tempI < processedText.length) {
                        val nextCp = processedText.codePointAt(tempI)
                        val charCount = Character.charCount(nextCp)
                        
                        val isZ = (nextCp == 0x200D)
                        val isM = (nextCp in 0x1F3FB..0x1F3FF) || nextCp == 0xFE0E || nextCp == 0xFE0F || nextCp == 0x20E3
                        val isR = (nextCp in 0x1F1E6..0x1F1FF)
                        val isB = (nextCp > 0xFFFF && !isM && !isR) || nextCp in 0x2600..0x27BF || nextCp in 0x2300..0x23FF || nextCp in 0x2B00..0x2BFF
                        
                        if (!isZ && !isM && !isR && !isB) break
                        
                        if (isR) {
                            if (regionalCount >= 2) break
                            regionalCount++
                            lastWasJoiner = false
                        } else {
                            regionalCount = 0
                            if (isB) {
                                if (hasBase && !lastWasJoiner) break
                                hasBase = true
                                lastWasJoiner = false
                            } else if (isZ) lastWasJoiner = true
                        }
                        sequenceLength += charCount
                        tempI += charCount
                    }
                    
                    sb.append("?")
                    
                    if (processedCaret > i) {
                        if (processedCaret >= i + sequenceLength) processedCaret -= (sequenceLength - 1)
                        else processedCaret = sb.length
                    }
                    i += sequenceLength
                } else {
                    sb.appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
            }
        }
        processedText = sb.toString()
        
        if (processedText.contains(":")) {
            val sb2 = java.lang.StringBuilder()
            i = 0
            while (i < processedText.length) {
                if (processedText[i] == ':') {
                    val maxSearch = kotlin.math.min(i + 30, processedText.length)
                    var closingColon = -1
                    for (k in i + 1 until maxSearch) {
                        if (processedText[k] == ':') { closingColon = k; break }
                    }
                    if (closingColon != -1) {
                        val candidate = processedText.substring(i, closingColon + 1)
                        val emoji = EmojiShortcodes.mappings[candidate] ?: EmojiShortcodes.shortcodeToNative[candidate]

                        // Explicitly IGNORE server emojis so they stay perfectly readable as `:valorhmm:`
                        if (emoji != null && !EmojiShortcodes.serverEmojis.containsKey(candidate)) {
                            sb2.append(emoji)
                            if (processedCaret > closingColon) processedCaret -= (candidate.length - emoji.length)
                            else if (processedCaret > i && processedCaret <= closingColon) processedCaret = sb2.length
                            i = closingColon + 1
                            continue
                        }
                    }
                }
                sb2.append(processedText[i])
                i++
            }
            processedText = sb2.toString()
        }
        
        textSetter(processedText)
        caret = processedCaret.coerceIn(0, processedText.length)
        clearSelection()
    }
    
    private fun deleteSelection() {
        if (caret == selection) return
        textSetter(text.removeRangeSafe(caret, selection))
        caret = if (selection > caret) caret else selection
        saveState()
    }
    
    private fun caretFromMouse(mouseX: Float) {
        val mx = mouseX - (x + textOffset)
        
        var currWidth = 0f
        var newCaret = 0
        
        for (index in text.indices) {
            val charWidth = textWidth(text[index].toString())
            if ((currWidth + charWidth / 2) > mx) break
            currWidth += charWidth
            newCaret = index + 1
        }
        caret = newCaret
        updateCaretPosition()
    }

    private fun updateCaretPosition() {
        if (selection != caret) {
            selectionWidth = textWidth(text.substringSafe(selection, caret))
            if (selection <= caret) selectionWidth *= -1
        } else selectionWidth = 0f

        if (caret != 0) {
            val previousX = caretX
            caretX = textWidth(text.substringSafe(0, caret))

            if (previousX < caretX) {
                if (caretX - textOffset >= width) textOffset = caretX - width
            } else {
                if (caretX - textOffset <= 0f) textOffset = textWidth(text.substringSafe(0, caret - 1))
            }

            if (textOffset > 0 && textWidth(text) - textOffset < width)
                textOffset = (textWidth(text) - width).coerceAtLeast(0f)
        } else {
            caretX = 0f
            textOffset = 0f
        }
    }

    private fun clearSelection() {
        selection = caret
        selectionWidth = 0f
    }

    private fun selectWord() {
        var start = caret
        var end = caret
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++

        selection = start
        caret = end
        updateCaretPosition()
    }

    private fun getPreviousSpace(): Int {
        var start = caret
        while (start > 0) {
            if (start != caret && text[start - 1].isWhitespace()) break
            start--
        }
        return start
    }

    private fun getNextSpace(): Int {
        var end = caret
        while (end < text.length) {
            if (end != caret && text[end].isWhitespace()) break
            end++
        }
        return end
    }

    private fun textWidth(text: String): Float = NVGRenderer.textWidth(text, height - 2, NVGRenderer.defaultFont)

    private fun resetState() {
        listening = false
        textOffset = 0f
        clearSelection()
    }

    private fun selectAll() {
        selection = 0
        caret = text.length
        updateCaretPosition()
    }

    private fun saveState() {
        if (text == lastSavedText) return

        if (historyIndex < history.size - 1) history.subList(historyIndex + 1, history.size).clear()

        history.add(text)
        historyIndex = history.size - 1
        lastSavedText = text
    }

    private fun undo() {
        if (historyIndex <= 0) return

        historyIndex--
        textSetter(history[historyIndex])
        caret = text.length
        selection = caret
        lastSavedText = text
    }

    private fun redo() {
        if (historyIndex >= history.size - 1) return

        historyIndex++
        textSetter(history[historyIndex])
        caret = text.length
        selection = caret
        lastSavedText = text
    }
    
    private fun isAreaHovered(x: Float, y: Float, width: Float, height: Float): Boolean {
        val mouseX = previousMousePos.first
        val mouseY = previousMousePos.second
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }
    
    // Completely hardened boundaries to stop index crashing
    private fun String.substringSafe(from: Int, to: Int): String {
        val f = min(from, to).coerceIn(0, length)
        val t = max(to, from).coerceIn(0, length)
        return substring(f, t)
    }

    private fun String.removeRangeSafe(from: Int, to: Int): String {
        val f = min(from, to).coerceIn(0, length)
        val t = max(to, from).coerceIn(0, length)
        return removeRange(f, t)
    }

    private fun String.dropAt(at: Int, amount: Int): String =
        removeRangeSafe(at, at + amount)
}
