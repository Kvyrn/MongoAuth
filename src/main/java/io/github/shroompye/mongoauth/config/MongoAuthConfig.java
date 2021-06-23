package io.github.shroompye.mongoauth.config;

import com.google.common.collect.ImmutableList;
import com.oroarmor.config.*;
import io.github.shroompye.mongoauth.MongoAuth;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;

import static com.google.common.collect.ImmutableList.of;

public class MongoAuthConfig extends Config {
    public MongoAuthConfig() {
        super(of(
                new AuthConfig(),
                new DatabaseInfo(),
                new PlayerActions(),
                new Privacy(),
                new Language(),
                new Debug()
        ), FabricLoader.getInstance().getConfigDir().resolve("mongo-auth.json").toFile(), MongoAuth.modid);
    }

    /**
     * Authentication settings.
     */
    public static class AuthConfig extends ConfigItemGroup {
        /**
         * Requires account to register. Disable this if only requrement is to have online players in online mode and not have offline player authentication.
         */
        public static final BooleanConfigItem requreAccount = new BooleanConfigItem("require-registration", true, "require-registration");
        /**
         * First try to authenticate with mojang, fallback to offline. Requires server in online mode.
         */
        public static final BooleanConfigItem mojangLogin = new BooleanConfigItem("optional-mojang-login", true, "optional-mojang-login");
        /**
         * Require a master password to register.
         */
        public static final BooleanConfigItem passwordRegister = new BooleanConfigItem("require-password-to-register", false, "require-password-to-register");
        /**
         * Usernames forced into offline mode, regardless of mojang account exisrtance.
         */
        public static final ArrayConfigItem<String> offlineNames = new ArrayConfigItem<>("forced-offline-names", new String[]{""}, "forced-offline-names");
        /**
         * Below 1 is disabled
         */
        public static final IntegerConfigItem sessionTime = new IntegerConfigItem("session-time", 60, "session-time-sec");
        /**
         * Kick after some time in unauthenticated state
         */
        public static final IntegerConfigItem kickTimer = new IntegerConfigItem("authentication-time", 60, "authentication-time");

        public AuthConfig() {
            super(of(requreAccount, mojangLogin, passwordRegister, offlineNames, sessionTime, kickTimer), "auth");
        }
    }

    /**
     * Database settings/credentials
     */
    public static class DatabaseInfo extends ConfigItemGroup {
        public static final StringConfigItem address = new StringConfigItem("address", "localhost", "address");
        public static final StringConfigItem username = new StringConfigItem("username", "", "username");
        public static final StringConfigItem userSourceDB = new StringConfigItem("user-source-database", "", "user-source-database");
        public static final StringConfigItem password = new StringConfigItem("password", "", "password");
        public static final StringConfigItem database = new StringConfigItem("database", "MinecraftAuthentication", "database");
        /**
         * Used to identify server specific info.
         */
        public static final StringConfigItem serverId = new StringConfigItem("server-id-required", "server", "server-id-required");

        public DatabaseInfo() {
            super(of(address, username, userSourceDB, password, database, serverId), "database");
        }
    }

    /**
     * Actions the unauthenticated player is allowed to make.
     */
    public static class PlayerActions extends ConfigItemGroup {
        public static final BooleanConfigItem unauthenticatedChatting = new BooleanConfigItem("allow-chatting", false, "allow-chatting");

        private PlayerActions() {
            super(of(unauthenticatedChatting), "playerActions");
        }
    }

    /**
     * Player info hiding, such as position and inventory.
     */
    public static class Privacy extends ConfigItemGroup {
        public static final BooleanConfigItem anounceAuthentication = new BooleanConfigItem("announce-authentication", false, "announce-authentication");
        public static final BooleanConfigItem hideInventory = new BooleanConfigItem("hide-inventory", true, "hide-inventory");
        public static final BooleanConfigItem hidePosition = new BooleanConfigItem("hide-position", true, "hide-position");
        public static final IntegerConfigItem hiddenYLevel = new IntegerConfigItem("hidden-y-level", -55, "hidden-y-level");
        public static final BooleanConfigItem showInPlayerList = new BooleanConfigItem("show-in-player-list", false, "show-in-player-list");
        public static final FormattingConfigItem playerListColor = new FormattingConfigItem("player-list-color", Formatting.RESET, "player-list-color");

        private Privacy() {
            super(of(anounceAuthentication, hideInventory, hidePosition, hiddenYLevel, showInPlayerList, playerListColor), "privacy");
        }
    }

    public static class Language extends ConfigItemGroup {
        public static final StringConfigItem tooLongToLogIn = new StringConfigItem("too-long-too-log-in", "Took too long to log in", "too-long-too-log-in");
        public static final StringConfigItem registrationRequired = new StringConfigItem("registration-required", "You are not registered! Use /register.", "registration-required");
        public static final StringConfigItem logIn = new StringConfigItem("log-in", "Log in with /login", "log-in");
        public static final StringConfigItem wrongPassword = new StringConfigItem("wrong-password", "Wrong password!", "wrong-password");
        public static final StringConfigItem unmatchingPassword = new StringConfigItem("unmatching-password", "Unmatching password!", "unmatching-password");
        public static final StringConfigItem alredyRegistered = new StringConfigItem("alredy-registered", "You are alredy registered", "alredy-registered");
        public static final StringConfigItem alredyLoggedIn = new StringConfigItem("alredy-logged-in", "You are alredy logged in", "alredy-logged-in");
        public static final StringConfigItem alredyLoggedOut = new StringConfigItem("alredy-logged-out", "You are alredy logged out", "alredy-logged-out");
        public static final StringConfigItem refreshAuthCommandSuggestion1 = new StringConfigItem("refreshauth-suggestion1", "If you changed your password, use ", "refreshauth-suggestion1");
        public static final StringConfigItem refreshAuthCommandSuggestion2 = new StringConfigItem("refreshauth-suggestion2", "/refreshauth", "refreshauth-suggestion2");
        public static final StringConfigItem refreshAuthCommandSuggestion3 = new StringConfigItem("refreshauth-suggestion3", " to update it.", "refreshauth-suggestion3");
        public static final StringConfigItem logInSuccesful = new StringConfigItem("login-succesful", "Logged in!", "login-succesful");
        public static final StringConfigItem logOutSuccesful = new StringConfigItem("logout-succesful", "Logged out!", "logout-succesful");

        public static final StringConfigItem cacheCleared = new StringConfigItem("cache-cleared", "Cache cleared", "cache-cleared");
        public static final StringConfigItem userRemoved = new StringConfigItem("user-removed", "User %s removed", "user-removed");
        public static final StringConfigItem userInexistent = new StringConfigItem("user-inexistent", "User %s does not exist!", "user-inexistent");
        public static final StringConfigItem passwordChanged = new StringConfigItem("password-changed", "Changed %s's password", "password-changed");
        public static final StringConfigItem globalPasswordChanged = new StringConfigItem("global-password-changed", "Global password changed", "global-password-changed");
        public static final StringConfigItem requirementUnchanged = new StringConfigItem("global-password-req-unchanged", "Global password requrement is alredy %s!", "global-password-req-unchanged");
        public static final StringConfigItem requirementSet = new StringConfigItem("global-password-req-changed", "Global password requrement set to %s!", "global-password-req-changed");

        public Language() {
            super(ImmutableList.of(tooLongToLogIn, registrationRequired, logIn, wrongPassword, unmatchingPassword, alredyRegistered,
                    alredyLoggedIn, alredyLoggedOut, refreshAuthCommandSuggestion1, refreshAuthCommandSuggestion2,
                    refreshAuthCommandSuggestion3, logInSuccesful, logOutSuccesful, cacheCleared, userRemoved, passwordChanged,
                    globalPasswordChanged, requirementUnchanged, requirementSet), "language");
        }
    }

    public static class Debug extends ConfigItemGroup {
        public static final BooleanConfigItem consoleAuthAnnounce = new BooleanConfigItem("announce-auth-console", false, "announce-auth-console");

        private Debug() {
            super(of(consoleAuthAnnounce), "debug");
        }
    }
}
