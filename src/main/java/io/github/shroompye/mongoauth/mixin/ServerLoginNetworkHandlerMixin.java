package io.github.shroompye.mongoauth.mixin;

import com.mojang.authlib.GameProfile;
import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.HasMongoAuthKeys;
import io.github.shroompye.mongoauth.util.KeysAuthHandler;
import io.github.shroompye.mongoauth.util.NetworkHandlerStateAccess;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.net.ssl.HttpsURLConnection;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.shroompye.mongoauth.MongoAuth.logNamed;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin implements NetworkHandlerStateAccess {
    @Shadow
    ServerLoginNetworkHandler.State state;
    @Shadow
    @Nullable GameProfile profile;

    @Shadow
    public abstract void disconnect(Text reason);

    @Shadow @Final public ClientConnection connection;

    @Shadow protected abstract GameProfile toOfflineProfile(GameProfile profile);

    @Inject(
            method = "onHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/packet/c2s/login/LoginHelloC2SPacket;getProfile()Lcom/mojang/authlib/GameProfile;",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onHello(LoginHelloC2SPacket packet, CallbackInfo ci) {
        HasMongoAuthKeys mongoAuthKeysInfo = (HasMongoAuthKeys) packet;
        if (MongoAuthConfig.CONFIG.debug.announceLogInAttempt)
            logNamed(packet.getProfile().getName() + " is trying to log in");
        if (MongoAuthConfig.CONFIG.auth.doMojangLogin) {
            try {
                String playername = packet.getProfile().getName().toLowerCase();
                Pattern pattern = Pattern.compile("^[a-z0-9_]{3,16}$");
                Matcher matcher = pattern.matcher(playername);
                if (!matcher.matches() || MongoAuth.playerForcedOffline(playername)) {
                    // Player definitely doesn't have a mojang account
                    if (!mongoAuthKeysInfo.hasMongoAuthKeys()) {
                        state = ServerLoginNetworkHandler.State.READY_TO_ACCEPT;
                        ci.cancel();
                    } else {
                        MongoAuth.playersWithMongoAuthKeys.add(playername);
                    }

                    this.profile = packet.getProfile();
                    if (MongoAuthConfig.CONFIG.debug.logMojangAccount)
                        logNamed(packet.getProfile().getName() + " doesn't have a mojang account, MongoAuthKeys: " + (mongoAuthKeysInfo.getMongoAuthKeysVersion()));
                } else if (!MongoAuth.onlineUsernames.contains(playername)) {
                    // Checking account status from API
                    HttpsURLConnection httpsURLConnection = (HttpsURLConnection) new URL("https://api.mojang.com/users/profiles/minecraft/" + playername).openConnection();
                    httpsURLConnection.setRequestMethod("GET");
                    httpsURLConnection.setConnectTimeout(5000);
                    httpsURLConnection.setReadTimeout(5000);

                    int response = httpsURLConnection.getResponseCode();
                    if (response == HttpURLConnection.HTTP_OK) {
                        // Player has a Mojang account
                        httpsURLConnection.disconnect();


                        // Caches the request
                        MongoAuth.onlineUsernames.add(playername);
                        // Authentication continues in original method

                        if (MongoAuthConfig.CONFIG.debug.logMojangAccount)
                            logNamed(packet.getProfile().getName() + " has a mojang account");
                    } else if (response == HttpURLConnection.HTTP_NO_CONTENT) {
                        // Player doesn't have a Mojang account
                        httpsURLConnection.disconnect();
                        if (!mongoAuthKeysInfo.hasMongoAuthKeys()) {
                            state = ServerLoginNetworkHandler.State.READY_TO_ACCEPT;
                            ci.cancel();
                        } else {
                            MongoAuth.playersWithMongoAuthKeys.add(playername);
                        }

                        this.profile = packet.getProfile();
                        if (MongoAuthConfig.CONFIG.debug.logMojangAccount)
                            logNamed(packet.getProfile().getName() + " doesn't have a mojang account, MongoAuthKeys: " + (mongoAuthKeysInfo.getMongoAuthKeysVersion()));
                    }
                }
            } catch (Exception e) {
                MongoAuth.logNamedError("Error verifying mojang account.", e);
                this.disconnect(new LiteralText(MongoAuthConfig.CONFIG.language.errorVerifyingAccount.formatted(e.toString())));
            }
        }
    }

    @Redirect(method = "onKey", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;start()V"))
    private void removeThreadOnKey(Thread instance) {
        if (!MongoAuthConfig.CONFIG.auth.doMojangLogin || (this.profile != null && MongoAuth.onlineUsernames.contains(this.profile.getName().toLowerCase()))) {
            instance.start();
        } else {
            //this.state = ServerLoginNetworkHandler.State.READY_TO_ACCEPT;
            //AuthRequestPackets.sendAuthRequest(connection, AuthRequestPackets.createPayload());

            // Player has MongoAuthKeys - request authentication
            this.profile = this.toOfflineProfile(this.profile);
            KeysAuthHandler authHandler = new KeysAuthHandler((ServerLoginNetworkHandler) ((Object)this), profile);
            MongoAuth.AUTH_HANDLERS.put(connection, authHandler);
            authHandler.sendPacket();
        }
    }

    @Override
    public ServerLoginNetworkHandler.State getState() {
        return state;
    }

    @Override
    public void setState(ServerLoginNetworkHandler.State state) {
        this.state = state;
    }

    @Override
    public GameProfile mongoauth_getGameProfile() {
        return profile;
    }
}
