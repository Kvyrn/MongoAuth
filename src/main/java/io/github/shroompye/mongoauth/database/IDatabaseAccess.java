package io.github.shroompye.mongoauth.database;

import io.github.shroompye.mongoauth.util.AuthData;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public interface IDatabaseAccess {
    boolean verifyGlobalPassword(String password);

    void setGlobalPassword(String password);


    AuthData getOrCreateAuthData(UUID uuid);

    void saveAuthData(AuthData authData);

    void deleteAuthData(UUID uuid);

    void refreshAuthData(UUID uuid);

    boolean authDataExists(UUID uuid, boolean cacheFound);

    void clearAuthDataCache();


    void storeInv(ServerPlayerEntity player);

    void restoreInv(ServerPlayerEntity player);

    void saveAuthPlayer(ServerPlayerEntity player);

    void loadAuthPlayer(ServerPlayerEntity player);
}
