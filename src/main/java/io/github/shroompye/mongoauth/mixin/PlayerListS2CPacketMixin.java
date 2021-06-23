package io.github.shroompye.mongoauth.mixin;

import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthData;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

@Mixin(PlayerListS2CPacket.class)
public class PlayerListS2CPacketMixin {
    @Shadow @Final private List<PlayerListS2CPacket.Entry> entries;

    @Inject(method = "<init>(Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket$Action;[Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("TAIL"))
    private void construct(PlayerListS2CPacket.Action action, ServerPlayerEntity[] players, CallbackInfo ci) {
        if (action == PlayerListS2CPacket.Action.ADD_PLAYER && !MongoAuthConfig.Privacy.showInPlayerList.getValue()) {
            entries.removeIf(entry -> {
                AuthData authData = MongoAuth.playerCache.getOrCreate(entry.getProfile().getId());
                return !authData.authenticated();
            });
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket$Action;Ljava/util/Collection;)V", at = @At("TAIL"))
    private void altConstruct(PlayerListS2CPacket.Action action, Collection<ServerPlayerEntity> players, CallbackInfo ci) {
        if (action == PlayerListS2CPacket.Action.ADD_PLAYER && !MongoAuthConfig.Privacy.showInPlayerList.getValue()) {
            entries.removeIf(entry -> {
                AuthData authData = MongoAuth.playerCache.getOrCreate(entry.getProfile().getId());
                return !authData.authenticated();
            });
        }
    }
}
