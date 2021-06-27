package io.github.shroompye.mongoauth.mixin;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ServerLoginNetworkHandler.class)
public class ServerLoginNetworkHandlerMixin {
    private static final Gson GSON = new Gson();

    @Shadow
    ServerLoginNetworkHandler.State state;
    @Shadow
    @Nullable GameProfile profile;

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
        if (MongoAuthConfig.Debug.logInAnnounceAttempt.getValue())
            MongoAuth.logNamed(packet.getProfile().getName() + " is trying to log in");
        if (MongoAuthConfig.AuthConfig.mojangLogin.getValue()) {
            try {
                String playername = packet.getProfile().getName().toLowerCase();
                Pattern pattern = Pattern.compile("^[a-z0-9_]{3,16}$");
                Matcher matcher = pattern.matcher(playername);
                if (!matcher.matches() || MongoAuth.playerForcedOffline(playername)) {
                    // Player definitely doesn't have a mojang account
                    state = ServerLoginNetworkHandler.State.READY_TO_ACCEPT;

                    this.profile = packet.getProfile();
                    ci.cancel();
                    if (MongoAuthConfig.Debug.mojangAccountOutput.getValue())
                        MongoAuth.logNamed(packet.getProfile().getName() + " doesn't have a mojang account");
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
                        if (MongoAuthConfig.Debug.mojangAccountOutput.getValue())
                            MongoAuth.logNamed(packet.getProfile().getName() + " has a mojang account");
                    } else if (response == HttpURLConnection.HTTP_NO_CONTENT) {
                        // Player doesn't have a Mojang account
                        httpsURLConnection.disconnect();
                        state = ServerLoginNetworkHandler.State.READY_TO_ACCEPT;

                        this.profile = packet.getProfile();
                        ci.cancel();
                        if (MongoAuthConfig.Debug.mojangAccountOutput.getValue())
                            MongoAuth.logNamed(packet.getProfile().getName() + " doesn't have a mojang account");
                    }
                }
            } catch (IOException e) {
                MongoAuth.logNamedError("Error verifying mojang account.", e);
            }
        }
    }
}
