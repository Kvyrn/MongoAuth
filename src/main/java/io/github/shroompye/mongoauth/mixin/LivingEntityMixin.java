package io.github.shroompye.mongoauth.mixin;

import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "tickStatusEffects", at = @At("HEAD"), cancellable = true)
    private void statusEffect(CallbackInfo ci) {
        mabeCancel(ci);
    }

    @Inject(method = "tickCramming", at = @At("HEAD"), cancellable = true)
    private void cramming(CallbackInfo ci) {
        mabeCancel(ci);
    }

    @Inject(method = "tickRiptide", at = @At("HEAD"), cancellable = true)
    private void riptide(CallbackInfo ci) {
        mabeCancel(ci);
    }

    @Inject(method = "tickActiveItemStack", at = @At("HEAD"), cancellable = true)
    private void stack(CallbackInfo ci) {
        mabeCancel(ci);
    }

    @Inject(method = "tickItemStackUsage", at = @At("HEAD"), cancellable = true)
    private void stack2(CallbackInfo ci) {
        mabeCancel(ci);
    }

    private void mabeCancel(CallbackInfo ci) {
        if (asLivingEntity() instanceof ServerPlayerEntity && !((AuthenticationPlayer) asLivingEntity()).isAuthenticated()) {
            ci.cancel();
        }
    }

    @SuppressWarnings("ConstantConditions")
    private LivingEntity asLivingEntity() {
        return (LivingEntity) ((Object) this);
    }
}
