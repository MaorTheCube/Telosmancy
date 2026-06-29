package me.telosmancy.mixin.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import me.telosmancy.features.impl.secret.GUIMoveModule;
import me.telosmancy.mixin.accessors.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.KeyboardInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Shadow @Final private Options options;

    @Inject(method = "tick", at = @At("HEAD"))
    private void injectGuiMovement(CallbackInfo ci) {
        if (!GUIMoveModule.isModuleEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;
        if (mc.screen instanceof ChatScreen) return;

        long window = mc.getWindow().handle();
        forceKey(options.keyUp, window);
        forceKey(options.keyDown, window);
        forceKey(options.keyLeft, window);
        forceKey(options.keyRight, window);
        forceKey(options.keyJump, window);
        forceKey(options.keyShift, window);
        forceKey(options.keySprint, window);
    }

    private static void forceKey(KeyMapping mapping, long window) {
        InputConstants.Key key = ((KeyMappingAccessor) mapping).getBoundKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            boolean down = GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
            KeyMapping.set(key, down);
        }
    }
}
