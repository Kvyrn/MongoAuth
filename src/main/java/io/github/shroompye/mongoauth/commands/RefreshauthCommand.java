package io.github.shroompye.mongoauth.commands;

import com.mojang.brigadier.CommandDispatcher;
import io.github.shroompye.mongoauth.MongoAuth;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.literal;

public class RefreshauthCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("refreshauth").executes(context -> {
            MongoAuth.databaseAccess.refreshAuthData(context.getSource().getPlayer().getUuid());
            return 1;
        }));
    }
}
