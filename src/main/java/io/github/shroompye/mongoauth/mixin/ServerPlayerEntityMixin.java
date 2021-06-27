package io.github.shroompye.mongoauth.mixin;

import com.mojang.authlib.GameProfile;
import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthData;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity implements AuthenticationPlayer {
    @Shadow
    public ServerPlayNetworkHandler networkHandler;
    @Shadow
    @Final
    public MinecraftServer server;
    @Unique
    private boolean authenticated = false;
    @Unique
    private Vec3d authPos = Vec3d.ZERO;
    @Unique
    private int kickTimer = MongoAuthConfig.AuthConfig.kickTimer.getValue() * 20;

    private ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        getAuthData().setAuthenticated(authenticated);
        if (!authenticated) {
            kickTimer = MongoAuthConfig.AuthConfig.kickTimer.getValue() * 20;
        } else {
            onAuthenticated();
        }
    }

    private void onAuthenticated() {
        if (MongoAuthConfig.Debug.consoleAuthAnnounce.getValue()) {
            MongoAuth.LOGGER.info("[" + MongoAuth.NAME + "] %s authenticated".formatted(this.getGameProfile().getName()));
        }
        if (MongoAuthConfig.Privacy.hideInventory.getValue()) MongoAuth.restoreInv(asPlayer());
        if (this.hasVehicle()) {
            this.getRootVehicle().setNoGravity(false);
            this.getRootVehicle().setInvulnerable(false);
        }
        this.setInvulnerable(false);
        this.setNoGravity(false);
        this.setInvisible(false);
        if (MongoAuthConfig.Privacy.hidePosition.getValue()) {
            this.teleport(authPos.x, authPos.y, authPos.z);
        }
        if (!MongoAuthConfig.Privacy.showInPlayerList.getValue()) {
            for (ServerPlayerEntity other : this.server.getPlayerManager().getPlayerList()) {
                other.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, asPlayer()));
            }
        } else {
            for (ServerPlayerEntity other : this.server.getPlayerManager().getPlayerList()) {
                other.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, asPlayer()));
            }
        }
        if (MongoAuthConfig.Debug.consoleAuthAnnounce.getValue()) {
            MongoAuth.logNamed(this.getGameProfile().getName() + " authenticated");
        }
    }

    @Override
    public void setAuthPos(Vec3d pos) {
        this.authPos = pos;
    }

    @Override
    public Vec3d getAuthPos() {
        return authPos;
    }

    @Inject(method = "getPlayerListName", at = @At("TAIL"), cancellable = true)
    private void getPlayerListName(CallbackInfoReturnable<Text> cir) {
        if (MongoAuthConfig.Privacy.showInPlayerList.getValue() && !isAuthenticated() && !MongoAuth.onlineUsernames.contains(this.getGameProfile().getName().toLowerCase(Locale.ROOT))) {
            Text returnV = cir.getReturnValue();
            Text displayName = getDisplayName();
            cir.setReturnValue((returnV == null ? displayName : returnV).copy().formatted(MongoAuthConfig.Privacy.playerListColor.getValue()));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        if (!isAuthenticated()) {
            if (kickTimer > 0) kickTimer--;
            if (kickTimer <= 0) {
                this.networkHandler.disconnect(new LiteralText(MongoAuthConfig.Language.tooLongToLogIn.getValue()).styled(style -> style.withColor(Formatting.RED)));
            }
        }
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void isInvulnerableTo(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (!isAuthenticated()) {
            cir.setReturnValue(true);
        }
    }

    @Override
    @NotNull
    public AuthData getAuthData() {
        return MongoAuth.playerCache.getOrCreate(this.uuid);
    }

    @Override
    public void load(Document document) {
        double x = document.getDouble("x");
        double y = document.getDouble("y");
        double z = document.getDouble("z");
        this.authPos = new Vec3d(x, y, z);
    }

    @Override
    public Document save() {
        Document document = new Document();
        Vec3d pos = authPos == null ? this.getPos() : authPos;
        document.put("x", pos.x);
        document.put("y", pos.y);
        document.put("z", pos.z);
        return document;
    }

    @SuppressWarnings("ConstantConditions")
    private ServerPlayerEntity asPlayer() {
        return (ServerPlayerEntity) ((Object) this);
    }

    @Override
    public void sientAuth() {
        authenticated = true;
        getAuthData().setAuthenticated(true);
    }
}
