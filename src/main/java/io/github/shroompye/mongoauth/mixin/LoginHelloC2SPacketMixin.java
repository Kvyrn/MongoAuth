package io.github.shroompye.mongoauth.mixin;

import io.github.shroompye.mongoauth.util.HasMongoAuthKeys;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoginHelloC2SPacket.class)
public class LoginHelloC2SPacketMixin implements HasMongoAuthKeys {
    @Unique
    private String mongoAuthKeysVersion = "none";

    @Inject(method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V", at = @At("TAIL"))
    private void readMetadata(PacketByteBuf buf, CallbackInfo ci) {
        try {
            mongoAuthKeysVersion = buf.readString();
        } catch (IndexOutOfBoundsException e) { // No metadata in packet
            //
        }
    }

    @Override
    public boolean hasMongoAuthKeys() {
        return !mongoAuthKeysVersion.equals("none");
    }

    @Override
    public String getMongoAuthKeysVersion() {
        return mongoAuthKeysVersion;
    }
}
