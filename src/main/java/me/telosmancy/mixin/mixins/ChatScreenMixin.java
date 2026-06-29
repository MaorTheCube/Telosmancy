package me.telosmancy.mixin.mixins;

import me.telosmancy.features.impl.misc.ChatModule;
import me.telosmancy.features.impl.misc.ItemLinkFeature;
import me.telosmancy.utils.emoji.EmojiShortcodes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts user inputs while the chat is open
 * Hooks render loops to process live text conversion (Emoji Shortcodes), render custom
 * Chat Tabs, and handles intercepting outgoing messages for the queueing system.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow protected EditBox input;

    /**
     * Handles Alt+Arrow hotkeys to swap chat tabs quickly
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void telosmancy$onAltArrowKeys(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (ChatModule.INSTANCE.keyPressed(keyEvent.key(), keyEvent.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Intercepts messages to the internal queue if we are currently switching server channels
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void telosmancy$queueOutgoingDuringSwitch(String string, boolean bl, CallbackInfo ci) {
        if (ChatModule.INSTANCE.interceptOutgoingMessage(string, bl)) {
            ci.cancel();
        }
    }

    /**
     * Tracks user-sent messages to help bypass server chat cooldown limits
     */
    @Inject(method = "handleChatInput", at = @At("RETURN"))
    private void telosmancy$recordMessageSendTime(String string, boolean bl, CallbackInfo ci) {
        if (!ci.isCancelled() && ChatModule.INSTANCE.getEnabled()) {
            ChatModule.INSTANCE.recordNativeMessageSent(string);
        }
    }

    /**
     * Replaces standard shortcodes with actual Emoji icons the instant they are completed.
     * We hook `render` because ChatScreen does not override `tick()`
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void telosmancy$processLiveChatInput(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (this.input != null) {
            EmojiShortcodes.INSTANCE.processEditBox(this.input);
        }
    }

    /**
     * Draws the Chat Tab UI above the standard chat input box
     */
    @Inject(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V")
    )
    private void telosmancy$renderChatTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        ChatModule.INSTANCE.renderTabs(graphics, mouseX, mouseY);
    }

    /**
     * Handles clicking the Chat Tabs UI (left) and realm right-click teleport.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void telosmancy$onMouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        // button 1 = right mouse button
        if (mouseButtonEvent.button() == 1) {
            if (ChatModule.INSTANCE.handleRealmRightClickAt(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                cir.setReturnValue(true);
                return;
            }
        }
        if (ChatModule.INSTANCE.mouseClicked(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Safely reverts the unicode Emojis back into server-safe `:shortcodes:` the exact moment the user hits Enter.
     */
    @ModifyVariable(
            method = "handleChatInput",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String telosmancy$convertEmojisToShortcodes(String message) {
        String sanitized = message.replace("\n", "").replace("\r", "");
        String withItems = ItemLinkFeature.INSTANCE.processOutgoing(sanitized);
        return EmojiShortcodes.INSTANCE.replaceEmojiWithShortcodes(withItems);
    }
}