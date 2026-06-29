package me.telosmancy.mixin.mixins;

import me.telosmancy.features.impl.misc.ChatModule;
import me.telosmancy.features.impl.misc.ChatTab;
import me.telosmancy.features.impl.misc.KeybindsModule;
import me.telosmancy.interfaces.ChatTabs;
import me.telosmancy.utils.emoji.EmojiReplacer;
import me.telosmancy.events.ChatPacketEvent;
import me.telosmancy.events.core.EventBus;
import me.telosmancy.utils.ChatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hooks into the Chat HUD to implement the visual Chat Tabs, route incoming messages
 * to their correct tabs, and extend the maximum message limit
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatTabs {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;
    @Shadow public abstract int getWidth();
    @Shadow public abstract double getScale();
    @Shadow protected abstract boolean isChatFocused();
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract void scrollChat(int pos);
    @Shadow public abstract void captureClickableText(ActiveTextCollector collector, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode);

    // Stores the individual chat history for every single tab
    @Unique
    private final Map<ChatTab, List<GuiMessage.Line>> telosmancy$tabDisplayQueues = new java.util.HashMap<>();

    // Tracks if the chat is currently resizing so we don't accidentally save duplicate messages
    @Unique
    private boolean telosmancy$isRescaling = false;

    /**
     * Gets the message history list for a specific tab
     */
    @Unique
    private List<GuiMessage.Line> telosmancy$getQueue(ChatTab tab) {
        return telosmancy$tabDisplayQueues.computeIfAbsent(tab, k -> new ArrayList<>());
    }

    /**
     * Replaces the currently visible chat messages with the history of the selected tab
     */
    @Override
    public void telosmancy$swapTab(ChatTab newTab) {
        this.trimmedMessages.clear();
        this.trimmedMessages.addAll(telosmancy$getQueue(newTab));

        // Reset scroll position so the user is looking at the newest messages
        this.chatScrollbarPos = 0;
        this.newMessageSinceScroll = false;
    }

    @Override
    public @Nullable Style telosmancy$getStyleAt(double mouseX, double mouseY) {
        if (this.trimmedMessages.isEmpty() || !this.isChatFocused()) return null;
        ActiveTextCollector.ClickableStyleFinder finder =
            new ActiveTextCollector.ClickableStyleFinder(this.minecraft.font, (int) mouseX, (int) mouseY);
        this.captureClickableText(finder, (int) mouseX, (int) mouseY, ChatComponent.DisplayMode.FOREGROUND);
        return finder.result();
    }

    @Override
    public @Nullable Component telosmancy$getMessageAt(double mouseX, double mouseY) {
        if (this.trimmedMessages.isEmpty() || !this.isChatFocused()) return null;
        double scale = this.getScale();
        int guiHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int lineStep = this.minecraft.font.lineHeight + 1;
        double chatBaseY = guiHeight / scale - 40.0;
        double chatMouseY = mouseY / scale;
        int lineIdx = (int) Math.floor((chatBaseY - chatMouseY) / lineStep);
        lineIdx += this.chatScrollbarPos;
        if (lineIdx < 0 || lineIdx >= this.trimmedMessages.size()) return null;
        GuiMessage.Line line = this.trimmedMessages.get(lineIdx);
        return line.parent().content();
    }

    /**
     * Triggered when the user resizes the game window or changes chat scale
     * Clear the queues and enable a flag to block transient messages from saving
     */
    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void telosmancy$onRefreshStart(CallbackInfo ci) {
        this.telosmancy$tabDisplayQueues.clear();
        this.telosmancy$isRescaling = true; // Block transient messages from re-rendering
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("RETURN"))
    private void telosmancy$onRefreshEnd(CallbackInfo ci) {
        this.telosmancy$isRescaling = false; // Restore state
    }

    /**
     * Intercepts messages before they are added to the screen so we can route them
     * to their specific tab or hide them
     */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void telosmancy$customAddMessageToQueue(GuiMessage message, CallbackInfo ci) {
        if (!ChatModule.INSTANCE.getEnabled() || !ChatModule.INSTANCE.isChatTabsEnabled()) return;

        // Process the message to figure out its tab category and if it should be censored
        ChatModule.ProcessedMessage processed = ChatModule.INSTANCE.getProcessedMessage(message.content());
        ChatTab messageTab = processed.getCategory();
        boolean isTransient = processed.isTransient();

        // Block useless utility messages entirely if the user enabled that setting
        if (messageTab == ChatTab.UTILITY && ChatModule.INSTANCE.isHideUtilityMessages()) {
            ci.cancel();
            return;
        }

        Component contentToWrap = processed.getCensoredContent();

        // Calculate the maximum width of the chat box based on the user's GUI scale settings
        int width = Mth.floor((double) this.getWidth() / this.getScale());

        // Break the long message into multiple lines so it fits on the screen
        List<FormattedCharSequence> wrappedLines = ComponentRenderUtils.wrapComponents(contentToWrap, width, this.minecraft.font);

        boolean chatFocused = this.isChatFocused();
        ChatTab activeTab = ChatModule.INSTANCE.getActiveTab();

        // Convert the wrapped text into Minecraft's native format
        List<GuiMessage.Line> newLines = new ArrayList<>();
        for (int i = 0; i < wrappedLines.size(); i++) {
            boolean isLast = (i == wrappedLines.size() - 1);
            newLines.add(new GuiMessage.Line(message, wrappedLines.get(i), isLast));
        }

        // Add to main history (ALL tab) as permanent log
        telosmancy$addToTabQueue(ChatTab.ALL, newLines, activeTab == ChatTab.ALL, chatFocused, false);

        if (isTransient) {
            // Transient messages are temporary (like error warnings)
            // Force display on the currently active tab but never save them to the background history
            if (activeTab != ChatTab.ALL && !this.telosmancy$isRescaling) {
                telosmancy$addToTabQueue(activeTab, newLines, true, chatFocused, true);
            }
        } else {
            // Check if it's a Callout, which requires rendering on both Chat and Callouts tabs
            if (messageTab == ChatTab.CALLOUTS) {
                telosmancy$addToTabQueue(ChatTab.CALLOUTS, newLines, activeTab == ChatTab.CALLOUTS, chatFocused, false);
                telosmancy$addToTabQueue(ChatTab.CHAT, newLines, activeTab == ChatTab.CHAT, chatFocused, false);
            } else if (messageTab != ChatTab.ALL) {
                // Standard categorized message queue
                telosmancy$addToTabQueue(messageTab, newLines, activeTab == messageTab, chatFocused, false);
            }
        }

        // Cancel vanilla method so we don't render the message twice
        ci.cancel();
    }

    /**
     * Pushes a message into a specific chat tab's history
     */
    @Unique
    private void telosmancy$addToTabQueue(ChatTab tab, List<GuiMessage.Line> newLines, boolean isActive, boolean chatFocused, boolean isTransient) {
        List<GuiMessage.Line> queue = telosmancy$getQueue(tab);

        for (GuiMessage.Line line : newLines) {
            // Only add to background persistent list if it is not a transient message
            if (!isTransient) {
                queue.addFirst(line);
            }

            // If the user is currently looking at this tab, show it on the screen immediately
            if (isActive) {
                this.trimmedMessages.addFirst(line);

                // If they are scrolled up to read old messages, push them down slightly so they don't lose their place
                if (chatFocused && this.chatScrollbarPos > 0) {
                    this.newMessageSinceScroll = true;
                    this.scrollChat(1);
                }
            }
        }

        // Clean up memory by dropping lines older than our custom max limit
        if (!isTransient) {
            while (queue.size() > 16384) {
                queue.removeLast();
            }
        }

        if (isActive) {
            while (this.trimmedMessages.size() > 16384) {
                this.trimmedMessages.removeLast();
            }
        }
    }

    /**
     * Replaces Minecraft's hardcoded 100-message limit 16,384
     */
    @ModifyConstant(
            method = {
                    "addMessageToDisplayQueue",
                    "addMessageToQueue"
            },
            constant = @Constant(intValue = 100)
    )
    private int telosmancy$extendChatLimit(int defaultLimit) {
        return 16384;
    }

    /**
     * Ticks our ChatModule logic before rendering chat elements
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    private void telosmancy$checkChatRefresh(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        ChatModule.INSTANCE.onRender(ticks);
    }

    /**
     * Fires ChatPacketEvent for player chat messages so item share (and other handlers) can intercept them.
     */
    @Inject(method = "addPlayerMessage", at = @At("HEAD"), cancellable = true)
    private void telosmancy$onPlayerMessage(Component message, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        String value = message.getString();
        EventBus.INSTANCE.post(new ChatPacketEvent(value, message));
        if (ChatManager.INSTANCE.shouldCancelMessage(message)) {
            ci.cancel();
        }
    }

    /**
     * Intercepts incoming messages to catch chat mode switches
     */
    @Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
    private void telosmancy$catchServerChatModeSwitch(Component message, CallbackInfo ci) {
        if (ChatModule.INSTANCE.handleChatModeMessage(message.getString())) {
            ci.cancel(); // Hide the raw server notification if we successfully handled it internally
        }
    }

    /**
     * Processes emojis and injects unconditionally the click-to-teleport logic to the component immediately.
     */
    @ModifyVariable(
            method = "addPlayerMessage",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component telosmancy$processIncomingMessage(Component message) {
        Component withEmojis = EmojiReplacer.INSTANCE.replaceIn(message);
        Component withCallout = KeybindsModule.INSTANCE.applyCalloutClickEvent(withEmojis);
        if (ChatModule.INSTANCE.isClickToTeleport()) {
            return KeybindsModule.INSTANCE.applyRealmJoinClickEvent(withCallout);
        }
        return withCallout;
    }
}