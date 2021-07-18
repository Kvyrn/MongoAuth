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

import java.util.Locale;

import static io.github.shroompye.mongoauth.MongoAuth.optionalyHide;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        try {
            player.requestRespawn();
            MongoAuth.loadAuthPlayer(player);
            if (MongoAuth.onlineUsernames.contains(player.getGameProfile().getName().toLowerCase(Locale.ROOT))) {
                ((AuthenticationPlayer)player).sientAuth();
                return;
            }
            AuthData authData = MongoAuth.playerCache.getOrCreate(player.getUuid());
            if (!MongoAuthConfig.config.auth().requireRegistration && !authData.registered()) {
                ((AuthenticationPlayer) player).sientAuth();
                return;
            }
            String ip = connection.getAddress().toString().split(":")[0];
            if (authData.hasValidSession(ip)) {
                ((AuthenticationPlayer)player).sientAuth();
                authData.makeSession(ip);
                return;
            }
            boolean leftUnathenticated = authData.didLeftUnathenticated();
            authData.setLeftUnathenticated(true);
            if (!leftUnathenticated) {
                optionalyHide(player);
            }
            ((AuthenticationPlayer) player).setAuthenticated(false);
            player.sendMessage(new LiteralText(authData.registered() ? MongoAuthConfig.config.language.logIn : MongoAuthConfig.config.language.registrationRequired).styled(style -> style.withColor(Formatting.DARK_PURPLE).withBold(true)), false);
            MongoAuth.saveAuthPlayer(player);
        } catch (Exception e) {
            MongoAuth.LOGGER.error("Player logging in", e);
            throw e;
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerLeave(ServerPlayerEntity player, CallbackInfo ci) {
        AuthData data = MongoAuth.playerCache.getOrCreate(player.getUuid());
        if (data.authenticated()) {
            data.makeSession(player.networkHandler.connection.getAddress().toString().split(":")[0]);
        }
        MongoAuth.playerCache.save(data);
    }
}
