package io.github.shroompye.mongoauth.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MongoAuthConfig {
    public static transient MongoAuthConfig CONFIG = new MongoAuthConfig();
    private static final Path path = FabricLoader.getInstance().getConfigDir().resolve("mongo-auth.conf");

    public static void load() {
        if (!path.toFile().exists()) {
            save();
            return;
        }
        CONFIG = ConfigHelper.load(path, MongoAuthConfig::new);
    }

    public static void save() {
        ConfigHelper.save(path, CONFIG, cfg -> {
            cfg.setComment("auth.requireRegistration", " When disabled, new players do not need to register, but won't be authenticated.");
            cfg.setComment("auth.doMojangLogin", " When enabled, authentication is first attempted with mojang.");
            cfg.setComment("auth.requrePasswordToRegister", " When enabled, players need a password to register.");
            cfg.setComment("auth.offlineNames", " Players with names in this list will never use mojang authentication.");
            cfg.setComment("auth.sessionTime", " In seconds, below 1 disables sessions.");
            cfg.setComment("auth.kickTime", " Time after wich unauthenticated players are kicked. In seconds.");

            cfg.setComment("databaseInfo.serverId", " Identifies this server. Should be unique among all servers using the same database.");
            cfg.setComment("databaseInfo.databaseType", " Must be one of: mongodb, mysql");
        });
    }

    public AuthConfigSection auth = new AuthConfigSection();

    public static class AuthConfigSection {
        public boolean requireRegistration = true;
        public boolean doMojangLogin = true;
        public boolean requrePasswordToRegister = false;
        public List<String> offlineNames = new ArrayList<>();
        public int sessionTime = 60;
        public int kickTime = 20;
    }

    public DatabaseInfoSection databaseInfo = new DatabaseInfoSection();

    public static class DatabaseInfoSection {
        public String serverId = "server";
        public String databaseType = "mongodb";

        public MongoDBSection mongoDB = new MongoDBSection();

        public static class MongoDBSection {
            public String address = "localhost";
            public String username = "";
            public String userSourceDatabase = "";
            public String password = "";
            public String database = "MinecraftAuth";
        }

        public MySQLSection mySQL = new MySQLSection();

        public static class MySQLSection {
            public String address = "localhost:3306/minecraftauth";
            public String username = "user";
            public String password = "pass";
        }
    }

    public PlayerActionsSection playerActions = new PlayerActionsSection();

    public static class PlayerActionsSection {
        public boolean allowChatting = false;
    }

    public PrivacySection privacy = new PrivacySection();

    public static class PrivacySection {
        public boolean announceAuthentication = false;
        public boolean hideInventory = true;
        public boolean hidePosition = true;
        public int hiddenYLevel = -50;
        public boolean showInPlayerList = false;
        public Formatting playerListColor = Formatting.RESET;
    }

    public LanguageSection language = new LanguageSection();

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
        public String errorVerifyingAccount = "Error verifying Mojang account!\n%s";
        public String invalidKey = "Failed to log in: invalid key";
        public String errorVerifyingKey = "Failed to log in: error verifying key!\nContact server administrator!";
        public String userAlredyExists = "Registration failed: user alredy exists";
        public String registrationInvalidkey = "Registration failed: invalid key";
    }

    public DebugSection debug = new DebugSection();

    public static class DebugSection {
        public boolean announceAuthConsole = false;
        public boolean announceLogInAttempt = false;
        public boolean logMojangAccount = false;
        public boolean logRegistration = false;
        public boolean doAuthHandlerCleaning = true;
    }
}
