package io.github.shroompye.mongoauth.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerLoginNetworkHandler;

public interface NetworkHandlerStateAccess {
    void setState(ServerLoginNetworkHandler.State state);
    ServerLoginNetworkHandler.State getState();
    GameProfile getGameProfile();
}
