package io.github.shroompye.mongoauth.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import org.bson.Document;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AuthData {
    public static final Argon2 ARGON_2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    @Nullable
    private AuthSession session;
    public final UUID uuid;
    private String paswordHash = null;
    private boolean authenticated = false;
    private boolean leftUnathenticated = false;

    public AuthData(UUID uuid) {
        this.uuid = uuid;
    }

    private AuthData(String passwordHash, @Nullable AuthSession session, UUID uuid) {
        this.session = session;
        this.uuid = uuid;
        this.paswordHash = passwordHash;
    }

    public boolean registered() {
        return paswordHash != null;
    }

    public void setPassword(String password) {
        paswordHash = createHash(password);
    }

    public void setPaswordHash(String paswordHash) {
        this.paswordHash = paswordHash;
    }

    public static String createHash(String password) {
        return ARGON_2.hash(4, 512*1024, 4, password);
    }

    public boolean hasValidSession(String ip) {
        return session != null && session.valid(ip);
    }

    public void makeSession(String ip) {
        if (MongoAuthConfig.AuthConfig.sessionTime.getValue() > 0) {
            this.session = new AuthSession(MongoAuthConfig.AuthConfig.sessionTime.getValue(), ip);
        }
    }

    public void removeSession() {
        this.session = null;
    }

    public Document asDocument() {
        Document document = new Document();
        document.put("passwordHash", paswordHash);
        document.put("uuid", uuid.toString());
        @SuppressWarnings("ConstantConditions") boolean hasSession = hasValidSession(session.ip);
        document.put("hasSession", hasSession);
        if (hasSession) {
            document.put("sessionExpiresOn", session.expiresOn);
            document.put("sessionIp", session.ip);
        }
        return document;
    }

    public static AuthData fromDocument(Document document) {
        String passwordHash = document.getString("passwordHash");
        UUID uuid = UUID.fromString(document.getString("uuid"));
        AuthSession session1 = null;
        if (document.getBoolean("hasSession")) {
            long sessionExpiery = document.getLong("sessionExpiresOn");
            String ip = document.getString("sessionIp");
            session1 = new AuthSession(sessionExpiery, ip);
        }
        return new AuthData(passwordHash, session1, uuid);
    }

    public boolean authenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean verifyPassword(String password) {
        return ARGON_2.verify(paswordHash, password);
    }

    public void setLeftUnathenticated(boolean leftUnathenticated) {
        this.leftUnathenticated = leftUnathenticated;
    }

    public boolean didLeftUnathenticated() {
        return leftUnathenticated;
    }

    public static class AuthSession {
        private final long expiresOn;
        private final String ip;

        public AuthSession(int expiresAfter, String ip) {
            this(System.currentTimeMillis() + expiresAfter * 1000L, ip);
        }

        public AuthSession(long expiresOn, String ip) {
            this.expiresOn = expiresOn;
            this.ip = ip;
        }

        public boolean valid(String ip) {
            if (!this.ip.equals(ip)) return false;
            return System.currentTimeMillis() < expiresOn;
        }
    }
}
