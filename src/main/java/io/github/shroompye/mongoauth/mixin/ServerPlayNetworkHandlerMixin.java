package io.github.shroompye.mongoauth.mixin;

import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.filter.TextStream;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = "net/minecraft/network/NetworkThreadUtils.forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (!authenticated(player)) {
            player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
            ci.cancel();
        }
    }

    @Inject(method = "onCreativeInventoryAction", at = @At(value = "INVOKE", target = "net/minecraft/network/NetworkThreadUtils.forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void cretiveInventoryAction(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (!authenticated(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", at = @At(value = "INVOKE", target = "net/minecraft/network/NetworkThreadUtils.forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (!authenticated(player)) {
            switch (packet.getAction()) {
                case ABORT_DESTROY_BLOCK:
                case STOP_DESTROY_BLOCK:
                case RELEASE_USE_ITEM:
                default:
                    break;
                case START_DESTROY_BLOCK:
                case DROP_ALL_ITEMS:
                case DROP_ITEM:
                case SWAP_ITEM_WITH_OFFHAND:
                    ci.cancel();
                    break;
            }
        }
    }

    @Inject(method = "onPlayerInteractBlock", cancellable = true, at = @At(value = "INVOKE", target = "net/minecraft/network/NetworkThreadUtils.forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER))
    private void onPlayerInteractBlock(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (!authenticated(player)) ci.cancel();
    }

    @Inject(method = "onPlayerInteractEntity", cancellable = true, at = @At(value = "INVOKE", target = "net/minecraft/network/NetworkThreadUtils.forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER))
    private void onPlayerInteractEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        if (!authenticated(player)) ci.cancel();
    }

    @Inject(method = "onPlayerInteractItem", cancellable = true, at = @At(value = "INVOKE", target = "net/minecraft/network/NetworkThreadUtils.forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER))
    private void onPlayerInteractItem(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (!authenticated(player)) ci.cancel();
    }

    @Inject(method = "handleMessage", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;updateLastActionTime()V", shift = At.Shift.AFTER))
    private void handleMessage(TextStream.Message message, CallbackInfo ci) {
        if (!MongoAuthConfig.CONFIG.playerActions.allowChatting && !authenticated(player) && !message.getRaw().startsWith("/login") && !message.getRaw().startsWith("/register") && !message.getRaw().startsWith("/refreshauth")) {
            ci.cancel();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean authenticated(ServerPlayerEntity player) {
        return MongoAuth.databaseAccess.getOrCreateAuthData(player.getUuid()).authenticated();
    }
}
