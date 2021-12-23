package io.github.shroompye.mongoauth.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

public class LogoutCommand {
    public static final SimpleCommandExceptionType NOT_LOGGED_IN = new SimpleCommandExceptionType(new LiteralText(MongoAuthConfig.config.language.alredyLoggedOut));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("logout").executes(context -> {
            AuthenticationPlayer authPlayer = (AuthenticationPlayer) context.getSource().getPlayer();
            if (authPlayer.isAuthenticated()) {
                MongoAuth.databaseAccess.getOrCreateAuthData(context.getSource().getPlayer().getUuid()).removeSession();
                authPlayer.setAuthenticated(false);
                MongoAuth.databaseAccess.saveAuthPlayer(context.getSource().getPlayer());
            } else {
                throw NOT_LOGGED_IN.create();
            }
            return 1;
        }));
    }
}
