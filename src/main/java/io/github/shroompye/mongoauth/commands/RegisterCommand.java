package io.github.shroompye.mongoauth.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthData;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RegisterCommand {
    public static final SimpleCommandExceptionType UNMATCHING_PASSWORD = new SimpleCommandExceptionType(new LiteralText(MongoAuthConfig.config.language.unmatchingPassword));
    public static final SimpleCommandExceptionType ALREDY_REGISTERED = new SimpleCommandExceptionType(new LiteralText(MongoAuthConfig.config.language.alredyRegistered));
    public static final SimpleCommandExceptionType WRONG_PASSWORD = new SimpleCommandExceptionType(new LiteralText(MongoAuthConfig.config.language.wrongPassword));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("register").then(argument("password", StringArgumentType.word()).then(argument("verifyPassword", StringArgumentType.word()).executes(context -> {
            doRegister(context);
            return 1;
        }).then(argument("globalPassword", StringArgumentType.word()).executes(context -> {
            if (MongoAuthConfig.config.auth.requrePasswordToRegister && !MongoAuth.databaseAccess.verifyGlobalPassword(StringArgumentType.getString(context, "globalPassword"))) throw WRONG_PASSWORD.create();
            doRegister(context);
            return 1;
        })))));
    }

    private static void doRegister(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        AuthData data = MongoAuth.databaseAccess.getOrCreateAuthData(context.getSource().getPlayer().getUuid());
        if (data.registered()) throw ALREDY_REGISTERED.create();

        String password = StringArgumentType.getString(context, "password");
        String verifyPassword = StringArgumentType.getString(context, "verifyPassword");
        if (!password.equals(verifyPassword)) throw UNMATCHING_PASSWORD.create();

        data.setPassword(password);
        ((AuthenticationPlayer)context.getSource().getPlayer()).setAuthenticated(true);
        data.setLeftUnathenticated(false);
        MongoAuth.databaseAccess.saveAuthData(data);

        if (MongoAuthConfig.config.debug.logRegistration) {
            MongoAuth.logNamed(context.getSource().getPlayer().getGameProfile().getName() + " registered with password");
        }
    }
}
