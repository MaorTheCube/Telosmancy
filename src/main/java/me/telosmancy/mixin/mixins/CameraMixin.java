package me.telosmancy.mixin.mixins;

import me.telosmancy.features.impl.secret.FOVChangerModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraMixin {

    // World perspective FOV — drives the projection matrix (what you see in the world)
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void modifyWorldFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        float extra = (float) FOVChangerModule.getExtraFov();
        if (extra != 0f) cir.setReturnValue(cir.getReturnValue() + extra);
    }

    // HUD / hand FOV — drives viewmodel / item-in-hand rendering
    @Inject(method = "calculateHudFov", at = @At("RETURN"), cancellable = true)
    private void modifyHudFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        float extra = (float) FOVChangerModule.getExtraFov();
        if (extra != 0f) cir.setReturnValue(cir.getReturnValue() + extra);
    }
}
