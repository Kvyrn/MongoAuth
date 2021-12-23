package io.github.shroompye.mongoauth;

import io.github.shroompye.mongoauth.commands.*;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.database.IDatabaseAccess;
import io.github.shroompye.mongoauth.database.MongoDatabaseAccess;
import io.github.shroompye.mongoauth.mixin.PlayerEntityAccessor;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;

public class MongoAuth implements ModInitializer {
    public static final String modid = "mongo-auth";
    public static final Logger LOGGER = LogManager.getLogger();
    public static IDatabaseAccess databaseAccess;
    public static final LinkedList<String> onlineUsernames = new LinkedList<>();
    public static String NAME = "";

    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer(modid).ifPresent(modContainer -> NAME = modContainer.getMetadata().getName());
        MongoAuthConfig.load();

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (((AuthenticationPlayer) oldPlayer).isAuthenticated()) {
                ((AuthenticationPlayer) newPlayer).silentAuth();
            } else {
                try {
                    //TODO: player respawns at spawn point
                    optionalyHideInvless(newPlayer);
                } catch (Exception e) {
                    logNamedError("Respawn unauth", e);
                    throw e;
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            LoginCommand.register(dispatcher);
            LogoutCommand.register(dispatcher);
            RegisterCommand.register(dispatcher);
            RefreshauthCommand.register(dispatcher);
            MongoAuthMainCommand.register(dispatcher);
        });

        databaseAccess = new MongoDatabaseAccess();
    }

    public static void logNamed(String str) {
        LOGGER.info("[" + NAME + "] " + str);
    }

    public static void logNamedError(String str, Exception e) {
        LOGGER.error("[" + NAME + "] " + str, e);
    }

    public static boolean playerForcedOffline(String name) {
        for (String s : MongoAuthConfig.config.auth().offlineNames) {
            if (name.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    public static void optionalyHide(ServerPlayerEntity player) {
        optionalyHideInvless(player);
        if (MongoAuthConfig.config.privacy.hideInventory) {
            MongoAuth.databaseAccess.storeInv(player);
            ((PlayerEntityAccessor) player).getInventory().clear();
        }
    }

    public static void optionalyHideInvless(ServerPlayerEntity player) {
        AuthenticationPlayer authPlayer = (AuthenticationPlayer) player;
        if (player.hasVehicle()) {
            player.getRootVehicle().setNoGravity(true);
            player.getRootVehicle().setInvulnerable(true);
        }
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.setInvisible(true);
        if (MongoAuthConfig.config.privacy.hidePosition) {
            authPlayer.setAuthPos(player.getPos());
            databaseAccess.saveAuthPlayer(player);
            player.requestTeleport(0.5d, MongoAuthConfig.config.privacy.hiddenYLevel, 0.5d);
        }
    }
}
