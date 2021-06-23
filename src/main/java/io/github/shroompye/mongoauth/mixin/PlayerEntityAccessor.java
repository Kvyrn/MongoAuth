package io.github.shroompye.mongoauth.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessor {
    @Accessor("inventory")
    PlayerInventory getInventory();

    @Accessor("inventory")
    void setInventory(PlayerInventory inventory);
}
