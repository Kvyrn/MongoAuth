package io.github.shroompye.mongoauth;

import io.github.shroompye.mongoauth.commands.*;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.database.IDatabaseAccess;
import io.github.shroompye.mongoauth.database.MongoDatabaseAccess;
import io.github.shroompye.mongoauth.database.MySQLDatabaseAccess;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import io.github.shroompye.mongoauth.util.KeysAuthHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

public class MongoAuth implements ModInitializer {
    public static final String modid = "mongo-auth";
    public static final Logger LOGGER = LogManager.getLogger("MongoAuth");
    public static IDatabaseAccess databaseAccess;
    public static final LinkedList<String> onlineUsernames = new LinkedList<>();
    public static final LinkedList<String> playersWithMongoAuthKeys = new LinkedList<>();
    public static final HashMap<ClientConnection, KeysAuthHandler> AUTH_HANDLERS = new HashMap<>();
    public static String NAME = "";

    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer(modid).ifPresent(modContainer -> NAME = modContainer.getMetadata().getName());
        MongoAuthConfig.load();
        MongoAuthConfig.save();

        if (MongoAuthConfig.CONFIG.auth.doMojangLogin && MongoAuthConfig.CONFIG.debug.doAuthHandlerCleaning) {
            AtomicReference<Thread> thread = new AtomicReference<>();

            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                thread.set(new Thread(() -> {
                    while (!server.isStopped()) {
                        for (ClientConnection connection : AUTH_HANDLERS.keySet()) {
                            if (!connection.isOpen()) {
                                AUTH_HANDLERS.remove(connection);
                            }
                        }
                        try {
                            Thread.sleep(120000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "MongoAuthCleaner"));
                thread.get().start();
            });
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
                if (thread.get() != null) {
                    thread.get().interrupt();
                }
            });
        }

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

        KeysAuthHandler.registerGlobalReciver();

        switch (MongoAuthConfig.CONFIG.databaseInfo.databaseType) {
            case "mongodb" -> databaseAccess = new MongoDatabaseAccess();
            case "mysql" -> {
                try {
                    databaseAccess = new MySQLDatabaseAccess();
                } catch (SQLException e) {
                    FabricGuiEntry.displayCriticalError(e, true);
                }
            }
            default -> FabricGuiEntry.displayCriticalError(new IllegalArgumentException("[" + NAME + "] Invalid database type: " + MongoAuthConfig.CONFIG.databaseInfo.databaseType), true);
        }
    }

    public static void logNamed(String str) {
        LOGGER.info("[" + NAME + "] " + str);
    }

    public static void logNamedError(String str, Exception e) {
        LOGGER.error("[" + NAME + "] " + str, e);
    }

    public static boolean playerForcedOffline(String name) {
        for (String s : MongoAuthConfig.CONFIG.auth.offlineNames) {
            if (name.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    public static void optionalyHide(ServerPlayerEntity player) {
        optionalyHideInvless(player);
        if (MongoAuthConfig.CONFIG.privacy.hideInventory) {
            MongoAuth.databaseAccess.storeInv(player);
            player.getInventory().clear();
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
        if (MongoAuthConfig.CONFIG.privacy.hidePosition) {
            authPlayer.setAuthPos(player.getPos());
            databaseAccess.saveAuthPlayer(player);
            player.requestTeleport(0.5d, MongoAuthConfig.CONFIG.privacy.hiddenYLevel, 0.5d);
        }
    }
}
