package io.github.shroompye.mongoauth.util;

import com.mojang.authlib.GameProfile;
import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import org.bson.internal.Base64;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

public class KeysAuthHandler {
    private static final Identifier MONGO_LOGIN_PACKET_IDENTIFIER = new Identifier(MongoAuth.modid, "login");
    //private static final Identifier MONGO_LOGIN_RESPONSE_PACKET_IDENTIFIER = new Identifier(MongoAuth.modid, "login-response");

    public final ServerLoginNetworkHandler networkHandler;
    public final GameProfile profile;
    private final byte[] payload;

    public KeysAuthHandler(ServerLoginNetworkHandler networkHandler, GameProfile profile) {
        this.networkHandler = networkHandler;
        this.profile = profile;
        payload = createPayload();
    }

    public static void registerGlobalReciver() {
        ServerLoginNetworking.registerGlobalReceiver(MONGO_LOGIN_PACKET_IDENTIFIER, (server, handler, understood, buf, synchronizer, responseSender) -> {
            KeysAuthHandler authHandler = MongoAuth.AUTH_HANDLERS.get(handler.connection);
            if (understood && buf.readBoolean() && authHandler != null) {
                    authHandler.recivePacket(buf.readBoolean(), buf.readByteArray());
            } else if (authHandler != null) {
                ((NetworkHandlerStateAccess) authHandler.networkHandler).setState(ServerLoginNetworkHandler.State.READY_TO_ACCEPT);
            }
        });
    }

    public void sendPacket() {
        PacketByteBuf buf = PacketByteBufs.create();
        boolean registered =
                MongoAuth.databaseAccess.authDataExists(profile.getId(), true) &&
                MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId()).registered();
        if (!registered) {
            buf.writeString("none");
        } else {
            AuthData authData = MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId());
            if (authData.getAuthenticationMethod() == AuthMethod.KEYS) {
                buf.writeString("keypair");
                buf.writeByteArray(payload);
            } else {
                buf.writeString("password");
            }
        }
        ServerNetworkingImpl.getAddon(networkHandler).sendPacket(MONGO_LOGIN_PACKET_IDENTIFIER, buf);
    }

    public void recivePacket(boolean isRegistered, byte[] input) {
        GameProfile profile = ((NetworkHandlerStateAccess) networkHandler).getGameProfile();
        if (isRegistered) {
            PublicKey key = getPublicKey(MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId()));
            boolean sigGood;
            try {
                Signature sig = Signature.getInstance("SHA256WithRSA");
                sig.initVerify(key);
                sig.update(this.payload);
                sigGood = sig.verify(input);
            } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
                MongoAuth.logNamedError("Error verifying key", e);
                networkHandler.disconnect(new LiteralText(MongoAuthConfig.config.language.errorVerifyingKey));
                return;
            }
            if (sigGood) {
                ((NetworkHandlerStateAccess) networkHandler).setState(ServerLoginNetworkHandler.State.READY_TO_ACCEPT);
                if (MongoAuthConfig.config.debug.announceAuthConsole) {
                    MongoAuth.logNamed(profile.getName() + " authenticated with key");
                }
            } else {
                networkHandler.disconnect(new LiteralText(MongoAuthConfig.config.language.invalidKey));
                if (MongoAuthConfig.config.debug.announceAuthConsole) {
                    MongoAuth.logNamed(profile.getName() + " failed to authenticate with key");
                }
            }
        } else {
            if (MongoAuth.databaseAccess.authDataExists(profile.getId(), true)
                    && MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId()).registered()) {
                AuthData data = MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId());
                if (data.getAuthenticationMethod() == AuthMethod.KEYS) {
                    networkHandler.disconnect(new LiteralText(MongoAuthConfig.config.language.userAlredyExists));
                } else {
                    MongoAuth.playersWithMongoAuthKeys.removeIf(s -> s.equals(profile.getName().toLowerCase(Locale.ROOT)));
                }
                return;
            }

            MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId()).setPaswordHash(encodeKey(input));
            ((NetworkHandlerStateAccess) networkHandler).setState(ServerLoginNetworkHandler.State.READY_TO_ACCEPT);

            if (MongoAuthConfig.config.debug.logRegistration) {
                MongoAuth.logNamed(profile.getName() + " registered with key");
            }
        }
    }

    private static PublicKey getPublicKey(AuthData authData) {
        if (!authData.registered() || !authData.getPaswordHash().startsWith("key")) {
            return null;
        }
        String encodedKey = authData.getPaswordHash().substring(3);
        byte[] rawKey = Base64.decode(encodedKey);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(rawKey));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            MongoAuth.logNamedError("Error decoding key", e);
            return null;
        }
    }

    private static String encodeKey(byte[] data) {
        return "key" + Base64.encode(data);
    }

    private static byte[] createPayload() {
        byte[] data = new byte[2048];
        new SecureRandom().nextBytes(data);
        return data;
    }
}
