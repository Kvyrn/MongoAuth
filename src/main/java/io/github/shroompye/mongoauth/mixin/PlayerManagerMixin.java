package io.github.shroompye.mongoauth.mixin;

import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthData;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        MongoAuth.loadAuthPlayer(player);
        if (MongoAuth.onlineUsernames.contains(player.getGameProfile().getName())) return;
        AuthData authData = MongoAuth.playerCache.getOrCreate(player.getUuid());
        if (!MongoAuthConfig.AuthConfig.requreAccount.getValue() && !authData.registered()) {
            ((AuthenticationPlayer) player).sientAuth();
            return;
        }
        String ip = connection.getAddress().toString().split(":")[0];
        if (authData.hasValidSession(ip)) {
            authData.makeSession(ip);
            return;
        }
        boolean leftUnathenticated = authData.didLeftUnathenticated();
        authData.setLeftUnathenticated(true);
        if (!leftUnathenticated) {
            optionalyHide(player);
        }
        ((AuthenticationPlayer) player).setAuthenticated(false);
        player.sendMessage(new LiteralText(authData.registered() ? MongoAuthConfig.Language.logIn.getValue() : MongoAuthConfig.Language.registrationRequired.getValue()).styled(style -> style.withColor(Formatting.DARK_PURPLE).withBold(true)), false);
        MongoAuth.saveAuthPlayer(player);
    }

    private void optionalyHide(ServerPlayerEntity player) {
        AuthenticationPlayer authPlayer = (AuthenticationPlayer) player;
        if (MongoAuthConfig.Privacy.hidePosition.getValue()) {
            authPlayer.setAuthPos(player.getPos());
            player.requestTeleport(0.5d, MongoAuthConfig.Privacy.hiddenYLevel.getValue(), 0.5d);
            if (player.hasVehicle()) {
                player.getRootVehicle().setNoGravity(true);
                player.getRootVehicle().setInvulnerable(true);
            }
            player.setInvulnerable(true);
            player.setNoGravity(true);
            player.setInvisible(true);
        }
        if (MongoAuthConfig.Privacy.hideInventory.getValue()) {
            MongoAuth.storeInv(player);
            ((PlayerEntityAccessor) player).getInventory().clear();
        }
    }
}
