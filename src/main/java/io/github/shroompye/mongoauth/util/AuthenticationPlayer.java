package io.github.shroompye.mongoauth.util;

import net.minecraft.util.math.Vec3d;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

public interface AuthenticationPlayer {
    boolean isAuthenticated();
    void setAuthenticated(boolean authenticated);
    void setAuthPos(Vec3d pos);
    Vec3d getAuthPos();
    @NotNull
    AuthData getAuthData();
    void load(Document document);
    Document save();
    void sientAuth();
}
