package io.github.shroompye.mongoauth.config;

import io.github.shroompye.mongoauth.MongoAuth;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigSerializable
public class MongoAuthConfig {
    public static MongoAuthConfig config = new MongoAuthConfig();
    private static final Path path = FabricLoader.getInstance().getConfigDir().resolve("mongo-auth.conf");
    private static final HoconConfigurationLoader CONFIG_LOADER = HoconConfigurationLoader.builder()
            .prettyPrinting(true)
            .path(path)
            .build();

    public static void load() {
        if (!path.toFile().exists()) {
            save();
            return;
        }
        CommentedConfigurationNode root;
        try {
            root = CONFIG_LOADER.load();
        } catch (ConfigurateException e) {
            MongoAuth.LOGGER.error("[" + MongoAuth.NAME + "] Error loading configuration. Default will be used.", e);
            return;
        }
        MongoAuthConfig conf;
        try {
            conf = root.get(MongoAuthConfig.class);
        } catch (SerializationException e) {
            MongoAuth.LOGGER.error("[" + MongoAuth.NAME + "] Error deserializing configuration. Default will be used.", e);
            return;
        }
        config = conf;
    }

    public static void save() {
        CommentedConfigurationNode root = null;
        try {
            root = CONFIG_LOADER.load();
        } catch (ConfigurateException e) {
            MongoAuth.LOGGER.error("[" + MongoAuth.NAME + "] Error saving configuration.", e);
        }
        try {
            root.set(MongoAuthConfig.class, config);
        } catch (SerializationException e) {
            MongoAuth.LOGGER.error("[" + MongoAuth.NAME + "] Error serializing configuration.", e);
            return;
        }
        try {
            CONFIG_LOADER.save(root);
        } catch (ConfigurateException e) {
            MongoAuth.LOGGER.error("[" + MongoAuth.NAME + "] Error saving configuration.", e);
        }
    }

    private AuthConfigSection auth = new AuthConfigSection();
    public AuthConfigSection auth() {
        return auth;
    }

    @ConfigSerializable
    public static class AuthConfigSection {
        @Comment("When disabled, new players do not need to register, but won't be authenticated.")
        public boolean requireRegistration = true;
        @Comment("When enabled, authentication is first attempted with mojang.")
        public boolean doMojangLogin = true;
        @Comment("When enabled, players need a password to register.")
        public boolean requrePasswordToRegister = false;
        @Comment("Players with names in this list will never use mojang authentication.")
        public List<String> offlineNames = new ArrayList<>();
        @Comment("In seconds, below 1 disables sessions.")
        public int sessionTime = 60;
        @Comment("Time after wich unauthenticated players are kicked. In seconds.")
        public int kickTime = 20;
    }

    public DatabaseInfoSection databaseInfo = new DatabaseInfoSection();

    @ConfigSerializable
    public static class DatabaseInfoSection {
        public String address = "localhost";
        public String username = "";
        public String userSourceDatabase = "";
        public String password = "";
        public String database = "MinecraftAuth";
        @Comment("Identifies this server. Should be unique among all servers using the same database.")
        public String serverId = "server";
    }

    public PlayerActionsSection playerActions = new PlayerActionsSection();

    @ConfigSerializable
    public static class PlayerActionsSection {
        public boolean allowChatting = false;
    }

    public PrivacySection privacy = new PrivacySection();

    @ConfigSerializable
    public static class PrivacySection {
        public boolean announceAuthentication = false;
        public boolean hideInventory = true;
        public boolean hidePosition = true;
        public int hiddenYLevel = -50;
        public boolean showInPlayerList = false;
        public Formatting playerListColor = Formatting.RESET;
    }

    public LanguageSection language = new LanguageSection();

    @ConfigSerializable
    public static class LanguageSection {
        public String tooLongToLogIn = "Took too long to log in";
        public String registrationRequired = "You are not registered! Use /register.";
        public String logIn = "Log in with /login";
        public String wrongPassword = "Wrong password!";
        public String unmatchingPassword = "Unmatching password!";
        public String alredyRegistered = "You are alredy registered";
        public String alredyLoggedIn = "You are alredy logged in";
        public String alredyLoggedOut = "You are alredy logged out";
        public boolean suggestRefreshAuthCommand = true;
        public String refreshAuthCommandSuggestion1 = "If you changed your password, use ";
        public String refreshAuthCommandSuggestion2 = "/refreshauth";
        public String refreshAuthCommandSuggestion3 = " to update it.";
        public String logInSuccesful = "Logged in!";
        public String logOutSuccesful = "Logged out!";
        public String cacheCleared = "Cache cleared";
        public String userRemoved = "User %s removed";
        public String userInexistent = "User %s does not exist!";
        public String passwordChanged = "Changed %s's password";
        public String globalPasswordChanged = "Global password changed";
        public String requirementUnchanged = "Global password requrement is alredy %s!";
        public String requirementSet = "Global password requrement set to %s!";
        public String sessionRemoved = "Removed %s's session";
        public String configReloaded = "Config reloaded";
    }

    public DebugSection debug = new DebugSection();

    @ConfigSerializable
    public static class DebugSection {
        public boolean announceAuthConsole = false;
        public boolean announceLogInAttempt = false;
        public boolean logMojangAccount = false;
    }
}
