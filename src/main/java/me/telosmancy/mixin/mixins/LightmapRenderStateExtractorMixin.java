package me.telosmancy.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.telosmancy.features.impl.visual.FullbrightModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin for LightTexture to provide fullbright via ambient light modification.
 * Based on NoFrills' Ambient mode implementation.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

    /**
     * Modify ambient light to provide fullbright effect
     * This is the cleanest method with no visual overlay
     */
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;vector3fFromRGB24(I)Lorg/joml/Vector3f;", ordinal = 2))
    private Vector3f telosmancy$modifyAmbientLight(Vector3f original) {
        if (FullbrightModule.INSTANCE.getEnabled()) {
            return new Vector3f(1.0f, 1.0f, 1.0f); // Full ambient light
        }
        return original;
    }
}
