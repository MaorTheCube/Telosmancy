package me.telosmancy.interfaces;

import me.telosmancy.features.impl.misc.ChatTab;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

public interface ChatTabs {
    void telosmancy$swapTab(ChatTab newTab);
    @Nullable Style telosmancy$getStyleAt(double mouseX, double mouseY);
    @Nullable Component telosmancy$getMessageAt(double mouseX, double mouseY);
}