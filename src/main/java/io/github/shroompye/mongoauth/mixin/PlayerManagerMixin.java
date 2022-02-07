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
            MongoAuth.databaseAccess.loadAuthPlayer(player);
            if (MongoAuth.onlineUsernames.contains(player.getGameProfile().getName().toLowerCase(Locale.ROOT)) ||
                    MongoAuth.playersWithMongoAuthKeys.contains(player.getGameProfile().getName().toLowerCase(Locale.ROOT))) {
                ((AuthenticationPlayer)player).silentAuth();
                return;
            }
            AuthData authData = MongoAuth.databaseAccess.getOrCreateAuthData(player.getUuid());
            if (!MongoAuthConfig.config.auth().requireRegistration && !authData.registered()) {
                ((AuthenticationPlayer) player).silentAuth();
                return;
            }
            String ip = connection.getAddress().toString().split(":")[0];
            if (authData.hasValidSession(ip)) {
                ((AuthenticationPlayer)player).silentAuth();
                authData.makeSession(ip);
                return;
            }
            boolean leftUnathenticated = authData.hasLeftUnathenticated();
            authData.setLeftUnathenticated(true);
            if (!leftUnathenticated) {
                optionalyHide(player);
            }
            ((AuthenticationPlayer) player).setAuthenticated(false);
            player.sendMessage(new LiteralText(authData.registered() ? MongoAuthConfig.config.language.logIn : MongoAuthConfig.config.language.registrationRequired).styled(style -> style.withColor(Formatting.DARK_PURPLE).withBold(true)), false);
            MongoAuth.databaseAccess.saveAuthPlayer(player);
        } catch (Exception e) {
            MongoAuth.LOGGER.error("Player logging in", e);
            throw e;
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerLeave(ServerPlayerEntity player, CallbackInfo ci) {
        AuthData data = MongoAuth.databaseAccess.getOrCreateAuthData(player.getUuid());
        if (data.authenticated()) {
            data.makeSession(player.networkHandler.connection.getAddress().toString().split(":")[0]);
        }
        MongoAuth.databaseAccess.saveAuthData(data);
    }
}
