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
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LoginCommand {
    public static final SimpleCommandExceptionType ALREDY_LOGGED_IN = new SimpleCommandExceptionType(new LiteralText(MongoAuthConfig.Language.alredyLoggedIn.getValue()));
    public static final SimpleCommandExceptionType NOT_REGISTERED = new SimpleCommandExceptionType(new LiteralText(MongoAuthConfig.Language.registrationRequired.getValue()));
    public static final SimpleCommandExceptionType WRONG_PASSWORD_SUGGESTED = new SimpleCommandExceptionType(
            new LiteralText(MongoAuthConfig.Language.wrongPassword.getValue())
                    .append(new LiteralText("\n" + MongoAuthConfig.Language.refreshAuthCommandSuggestion1.getValue()))
                    .append(new LiteralText(MongoAuthConfig.Language.refreshAuthCommandSuggestion2.getValue()).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/refreshauth")).withColor(Formatting.GOLD)))
                    .append(new LiteralText(MongoAuthConfig.Language.refreshAuthCommandSuggestion3.getValue())));
    public static final SimpleCommandExceptionType WRONG_PASSWORD = new SimpleCommandExceptionType(
            new LiteralText(MongoAuthConfig.Language.wrongPassword.getValue()));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("login").then(argument("password", StringArgumentType.word()).executes(LoginCommand::login)));
    }

    private static int login(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        try {
            AuthData data = MongoAuth.playerCache.getOrCreate(context.getSource().getPlayer().getUuid());
            if (data.authenticated()) throw ALREDY_LOGGED_IN.create();
            if (!data.registered()) throw NOT_REGISTERED.create();
            String password = StringArgumentType.getString(context, "password");
            if (!data.verifyPassword(password)) throw MongoAuthConfig.Language.suggestRefreshAuthCommand.getValue() ? WRONG_PASSWORD_SUGGESTED.create() : WRONG_PASSWORD.create();
            ((AuthenticationPlayer) context.getSource().getPlayer()).setAuthenticated(true);
            data.setLeftUnathenticated(false);
            MongoAuth.playerCache.save(data);
            return 1;
        } catch (Exception e) {
            MongoAuth.logNamedError("Error logging in", e);
            throw e;
        }
    }
}
