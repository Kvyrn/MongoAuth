package io.github.shroompye.mongoauth.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import static io.github.shroompye.mongoauth.MongoAuth.logNamedError;

public class ItemStachHelper {
    public static String itemStackToString(ItemStack item) {
        String itemData = item.writeNbt(new NbtCompound()).toString();
        return itemData.replace("'", "$");
    }

    public static ItemStack stringToItemStack(String nbt) {
        NbtCompound tag = null;
        try {
            tag = StringNbtReader.parse(nbt.replace("$", "'"));
        } catch (CommandSyntaxException e) {
            logNamedError("Error reading item stack", e);
        }
        return ItemStack.fromNbt(tag);
    }
}
