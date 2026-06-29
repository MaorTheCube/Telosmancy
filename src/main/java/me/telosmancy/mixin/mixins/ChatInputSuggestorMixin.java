package me.telosmancy.mixin.mixins;

import com.mojang.brigadier.suggestion.Suggestions;
import me.telosmancy.utils.emoji.EmojiSuggestionProvider;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Intercepts the game's auto-complete suggestion builder
 */
@Mixin(CommandSuggestions.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Shadow
    @Nullable
    private CommandSuggestions.SuggestionsList suggestions;

    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void telosmancy$setEmojiSuggestions(CallbackInfo ci) {
        if (this.input == null) return;

        String text = this.input.getValue();
        if (text == null || text.isEmpty()) return;

        // Bypasses logic entirely if the user is typing standard commands
        if (!EmojiSuggestionProvider.INSTANCE.isTypingEmoji(this.input)) return;

        CompletableFuture<Suggestions> emojiSuggestionsFuture = EmojiSuggestionProvider.INSTANCE.provideSuggestions(
                text, this.input.getCursorPosition()
        );

        // Dynamically combine mod suggestions with the server's live suggestion packet
        if (this.pendingSuggestions != null) {
            this.pendingSuggestions = this.pendingSuggestions.thenCombine(emojiSuggestionsFuture, (serverSugs, modSugs) ->
                    EmojiSuggestionProvider.INSTANCE.mergeAndCheckPerks(serverSugs, modSugs, text)
            );
        } else {
            this.pendingSuggestions = emojiSuggestionsFuture;
        }

        this.showSuggestions(false);
    }
}