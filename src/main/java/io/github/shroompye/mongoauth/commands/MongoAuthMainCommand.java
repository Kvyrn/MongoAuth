package io.github.shroompye.mongoauth.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthData;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MongoAuthMainCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal(MongoAuth.modid).requires(source -> source.hasPermissionLevel(3))
                                    .then(literal("clearCache").executes(MongoAuthMainCommand::clearCache))
                                    .then(literal("manageUser")
                                                  .then(literal("remove").then(argument("target", GameProfileArgumentType.gameProfile()).executes(MongoAuthMainCommand::removeUser)))
                                                  .then(literal("changePassword").then(argument("target", GameProfileArgumentType.gameProfile()).then(argument("password", StringArgumentType.word()).executes(MongoAuthMainCommand::changPassword))))
                                                  .then(literal("removeSession").then(argument("target", GameProfileArgumentType.gameProfile()).executes(MongoAuthMainCommand::removeSession))))
                                    .then(literal("setGlobalPassword").then(argument("password", StringArgumentType.word()).then(argument("verifyPassword", StringArgumentType.word()).executes(MongoAuthMainCommand::setGlobalPassword))))
                                    .then(literal("setGlobalPasswordRequirement").then(argument("value", BoolArgumentType.bool()).executes(MongoAuthMainCommand::setGlobalPasswordRequred)))
                                    .then(literal("reloadConfig").executes(MongoAuthMainCommand::reloadConfig))
        );
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        MongoAuthConfig.load();
        context.getSource().sendFeedback(new LiteralText(MongoAuthConfig.CONFIG.language.configReloaded), true);
        return 1;
    }

    private static int removeSession(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgumentType.getProfileArgument(ctx, "target");
        for (GameProfile profile : targets) {
            boolean exists = MongoAuth.databaseAccess.authDataExists(profile.getId(), false);
            if (exists) {
                MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId()).removeSession();
                ctx.getSource().sendFeedback(new LiteralText(MongoAuthConfig.CONFIG.language.sessionRemoved.formatted(profile.getName())), true);
            } else {
                ctx.getSource().sendError(new LiteralText(MongoAuthConfig.CONFIG.language.userInexistent.formatted(profile.getName())));
            }
        }
        return targets.size();
    }

    private static int setGlobalPasswordRequred(CommandContext<ServerCommandSource> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        if (MongoAuthConfig.CONFIG.auth.requrePasswordToRegister == value) {
            context.getSource().sendError(new LiteralText(MongoAuthConfig.CONFIG.language.requirementUnchanged.formatted(value)));
            return 0;
        } else {
            MongoAuthConfig.CONFIG.auth.requrePasswordToRegister = value;
            MongoAuthConfig.save();
            context.getSource().sendFeedback(new LiteralText(MongoAuthConfig.CONFIG.language.requirementSet.formatted(value)), true);
            return 1;
        }
    }

    private static int setGlobalPassword(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String password = StringArgumentType.getString(context, "password");
        String verifyPassword = StringArgumentType.getString(context, "verifyPassword");
        if (!password.equals(verifyPassword)) throw RegisterCommand.UNMATCHING_PASSWORD.create();

        MongoAuth.databaseAccess.setGlobalPassword(password);
        context.getSource().sendFeedback(new LiteralText(MongoAuthConfig.CONFIG.language.globalPasswordChanged), true);
        return 1;
    }

    private static int changPassword(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgumentType.getProfileArgument(ctx, "target");
        String passwordHash = AuthData.createHash(StringArgumentType.getString(ctx, "password"));
        for (GameProfile profile : targets) {
            boolean exists = MongoAuth.databaseAccess.authDataExists(profile.getId(), true);
            if (exists) {
                MongoAuth.databaseAccess.getOrCreateAuthData(profile.getId()).setPaswordHash(passwordHash);
                ctx.getSource().sendFeedback(new LiteralText(MongoAuthConfig.CONFIG.language.passwordChanged.formatted(profile.getName())), true);
            } else {
                ctx.getSource().sendError(new LiteralText(MongoAuthConfig.CONFIG.language.userInexistent.formatted(profile.getName())));
            }
        }
        return targets.size();
    }

    private static int clearCache(CommandContext<ServerCommandSource> ctx) {
        MongoAuth.databaseAccess.clearAuthDataCache();
        MongoAuth.onlineUsernames.clear();
        ctx.getSource().sendFeedback(new LiteralText(MongoAuthConfig.CONFIG.language.cacheCleared), true);
        return 1;
    }

    private static int removeUser(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgumentType.getProfileArgument(ctx, "target");
        for (GameProfile profile : targets) {
            boolean exists = MongoAuth.databaseAccess.authDataExists(profile.getId(), false);
            if (exists) {
                MongoAuth.databaseAccess.deleteAuthData(profile.getId());
                ctx.getSource().sendFeedback(new LiteralText(MongoAuthConfig.CONFIG.language.userRemoved.formatted(profile.getName())), true);
            } else {
                ctx.getSource().sendError(new LiteralText(MongoAuthConfig.CONFIG.language.userInexistent.formatted(profile.getName())));
            }
        }
        return targets.size();
    }
}
