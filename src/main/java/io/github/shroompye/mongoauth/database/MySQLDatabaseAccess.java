package io.github.shroompye.mongoauth.database;

import io.github.shroompye.mongoauth.MongoAuth;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthData;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Vec3d;

import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;

import static io.github.shroompye.mongoauth.util.AuthData.ARGON2;

public class MySQLDatabaseAccess implements IDatabaseAccess {
    private static final String getGlobalPassword = "SELECT Password FROM Globals ORDERED BY Timestamp DESC;";
    private static final String removeGlobalPassword = "DELETE FROM Globals;";
    private static final String insertGlobalPassword = "INSERT INTO Globals (Password) VALUES (?);";
    private static final String queryAuthData = "SELECT * FROM Players WHERE Uuid = ?;";
    private static final String checkAuthData = "SELECT 1 FROM Players WHERE Uuid = ?;";
    private static final String createAuthData = "INSERT INTO Players VALUES (?, ?, ?, ?, ?);";
    private static final String updateAuthData = "UPDATE Players SET Password = ?, LeftUnauthenticated = ?, SessionIP = ?, ExpiresOn = ? WHERE Uuid = ?;";
    private static final String removeAuthData = "DELETE FROM Players WHERE Uuid = ?;";
    private static final String insertInvSlot = "INSERT INTO Inventory VALUES(?, ?, ?, ?, ?, ?);";
    private static final String updateLastInvId = "UPDATE InventoryOwners SET LastInvId = ? WHERE Uuid = ? AND Server = ?;";
    private static final String getLastInvId = "SELECT LastInvId FROM InventoryOwners WHERE Uuid = ? AND Server = ?;";
    private static final String dropOldInvSlots = "DELETE FROM Inventory WHERE Id = ? AND Server = ? AND Uuid = ?;";
    private static final String getInvData = "SELECT Slot, Section, Value FROM Inventory WHERE Id = ? AND Server = ? AND Uuid = ?;";
    private static final String authPlayerDataExists = "SELECT 1 FROM ServerPlayers WHERE Uuid = ? AND Server = ?;";
    private static final String insertAuthPlayerData = "INSERT INTO ServerPlayers VALUES (?, ?, ?, ?, ?);";
    private static final String updateAuthPlayerData = "UPDATE ServerPlayers SET XPos = ?, YPos = ?, ZPos = ? WHERE Uuid = ? AND Server = ?;";
    private static final String getAuthPlayerData = "SELECT XPos, YPos, ZPos FROM ServerPlayers WHERE Uuid = ? AND Server = ?;";

    private final Connection connection;
    private final HashMap<UUID, AuthData> authDataCache = new HashMap<>();

    public MySQLDatabaseAccess() throws SQLException {
        connection = DriverManager.getConnection("jdbc:mysql://" + MongoAuthConfig.config.databaseInfo.mySQL.address, MongoAuthConfig.config.databaseInfo.mySQL.username, MongoAuthConfig.config.databaseInfo.mySQL.password);

        // Create tables
        try (PreparedStatement createGlobalsTable = connection.prepareStatement("CREATE TABLE IF NOT EXISTS Globals (" +
                "Password TINYTEXT," +
                "Timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP);")) {
            createGlobalsTable.executeUpdate();
        } catch (SQLException e) {
            logException(e, "creating globals table");
        }

        try (PreparedStatement createInventoryTable = connection.prepareStatement("CREATE TABLE IF NOT EXISTS Inventory (" +
                "Id BIGINT," +
                "Server TINYTEXT," +
                "Uuid CHAR(36)," +
                "Slot INT," +
                "Section ENUM('main', 'armor', 'offhand')," +
                "Value MEDIUMTEXT);")) {
            createInventoryTable.executeUpdate();
        } catch (SQLException e) {
            logException(e, "creating globals table");
        }

        try (PreparedStatement createInventoryOwnersTable = connection.prepareStatement("CREATE TABLE IF NOT EXISTS InventoryOwners (" +
                "Uuid CHAR(36)," +
                "Server TINYTEXT," +
                "LastInvId BIGINT);")) {
            createInventoryOwnersTable.executeUpdate();
        } catch (SQLException e) {
            logException(e, "creating globals table");
        }

        try (PreparedStatement createServerPlayersTable = connection.prepareStatement("CREATE TABLE IF NOT EXISTS ServerPlayers (" +
                "Uuid CHAR(36)," +
                "Server TINYTEXT," +
                "XPos DOUBLE," +
                "YPos DOUBLE," +
                "ZPos DOUBLE);")) {
            createServerPlayersTable.executeUpdate();
        } catch (SQLException e) {
            logException(e, "creating globals table");
        }

        try (PreparedStatement createPlayersTable = connection.prepareStatement("CREATE TABLE IF NOT EXISTS Players (" +
                "Uuid CHAR(36)," +
                "Password TINYTEXT," +
                "LeftUnauthenticated BOOL," +
                "SessionIP TINYTEXT," +
                "ExpiresOn BIGINT," +
                "PRIMARY KEY (Uuid));")) {
            createPlayersTable.executeUpdate();
        } catch (SQLException e) {
            logException(e, "creating globals table");
        }
    }

    @Override
    public boolean verifyGlobalPassword(String password) {
        try {
            PreparedStatement statement = connection.prepareStatement(getGlobalPassword);
            ResultSet set = statement.executeQuery();
            if (set.next()) {
                String hash = set.getString("Password");
                return ARGON2.verify(hash, password.toCharArray());
            }
        } catch (SQLException e) {
            logException(e, "verifyGlobalPassword");
        }
        return false;
    }

    @Override
    public void setGlobalPassword(String password) {
        try {
            PreparedStatement remove = connection.prepareStatement(removeGlobalPassword);
            remove.executeUpdate();

            PreparedStatement insert = connection.prepareStatement(insertGlobalPassword);
            insert.setString(1, AuthData.createHash(password));
            insert.executeUpdate();
        } catch (SQLException e) {
            logException(e, "setGlobalPassword");
        }
    }

    @Override
    public AuthData getOrCreateAuthData(UUID uuid) {
        if (authDataCache.containsKey(uuid)) return authDataCache.get(uuid);
        AuthData object = null;
        try {
            PreparedStatement statement = connection.prepareStatement(queryAuthData);
            statement.setString(1, uuid.toString());
            ResultSet set = statement.executeQuery();
            if (set.next()) {
                String password = set.getString("Password");
                boolean leftUnauthenticated = set.getBoolean("LeftUnauthenticated");
                String sessionIP = set.getString("SessionIP");
                long expiresOn = set.getLong("ExpiresOn");
                AuthData.AuthSession session = new AuthData.AuthSession(expiresOn, sessionIP);
                object = new AuthData(password, session.hasExpired() ? null : session, uuid, leftUnauthenticated);
            }
        } catch (SQLException e) {
            logException(e, "getOrCreateAuthData");
        }
        if (object == null) {
            object = new AuthData(uuid);
            saveAuthData(object);
        }
        authDataCache.put(uuid, object);
        return object;
    }

    @Override
    public void saveAuthData(AuthData authData) {
        try {
            boolean hasValidSession = authData.hasValidSession();
            MongoAuth.logNamed("Sesion for " + authData.uuid + ": " + hasValidSession);

            if (authDataExists(authData.uuid, false)) {
                PreparedStatement update = connection.prepareStatement(updateAuthData);
                update.setString(1, authData.getPaswordHash());
                update.setBoolean(2, authData.hasLeftUnathenticated());
                //noinspection ConstantConditions
                update.setString(3, hasValidSession ? authData.session.ip : "a");
                update.setLong(4, hasValidSession ? authData.session.expiresOn : 0);

                update.setString(5, authData.uuid.toString());
                update.executeUpdate();
            } else {
                PreparedStatement create = connection.prepareStatement(createAuthData);
                create.setString(1, authData.uuid.toString());
                create.setString(2, authData.getPaswordHash());
                create.setBoolean(3, authData.hasLeftUnathenticated());
                //noinspection ConstantConditions
                create.setString(4, hasValidSession ? authData.session.ip : "a");
                create.setLong(5, hasValidSession ? authData.session.expiresOn : 0);
                create.executeUpdate();
            }
        } catch (SQLException e) {
            logException(e, "saveAuthData");
        }
    }

    @Override
    public void deleteAuthData(UUID uuid) {
        authDataCache.remove(uuid);
        try {
            PreparedStatement statement = connection.prepareStatement(removeAuthData);
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            logException(e, "seleteAuthData");
        }
    }

    @Override
    public void refreshAuthData(UUID uuid) {
        authDataCache.remove(uuid);
    }

    @Override
    public boolean authDataExists(UUID uuid, boolean cacheFound) {
        try {
            PreparedStatement statement = connection.prepareStatement(checkAuthData);
            statement.setString(1, uuid.toString());
            return statement.executeQuery().next();
        } catch (SQLException e) {
            logException(e, "authDataExists");
        }
        return false;
    }

    @Override
    public void clearAuthDataCache() {
        authDataCache.clear();
    }

    private long getLastInvId(UUID uuid) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(getLastInvId);
        statement.setString(1, uuid.toString());
        statement.setString(2, MongoAuthConfig.config.databaseInfo.serverId);
        ResultSet set = statement.executeQuery();
        if (set.next()) {
            return set.getLong("LastInvId");
        } else {
            return -1;
        }
    }

    @Override
    public void storeInv(ServerPlayerEntity player) {
        long id = System.currentTimeMillis();

        PlayerInventory inventory = player.getInventory();
        DefaultedList<String> mainStr = DefaultedList.ofSize(inventory.main.size(), "");
        DefaultedList<String> armorStr = DefaultedList.ofSize(inventory.armor.size(), "");
        DefaultedList<String> offhandStr = DefaultedList.ofSize(inventory.offHand.size(), "");
        MongoDatabaseAccess.stringify(inventory.main, mainStr);
        MongoDatabaseAccess.stringify(inventory.armor, armorStr);
        MongoDatabaseAccess.stringify(inventory.offHand, offhandStr);

        try {
            long prevInvId = getLastInvId(player.getUuid());

            PreparedStatement insertInv = connection.prepareStatement(insertInvSlot);
            insertInv.setLong(1, id);
            insertInv.setString(2, MongoAuthConfig.config.databaseInfo.serverId);
            insertInv.setString(3, player.getUuidAsString());

            insertInv.setString(5, "main");
            for (int index = 0; index < mainStr.size(); index++) {
                insertInv.setInt(4, index);
                insertInv.setString(6, mainStr.get(index));
                insertInv.executeUpdate();
            }

            insertInv.setString(5, "armor");
            for (int index = 0; index < armorStr.size(); index++) {
                insertInv.setInt(4, index);
                insertInv.setString(6, armorStr.get(index));
                insertInv.executeUpdate();
            }

            insertInv.setString(5, "offhand");
            for (int index = 0; index < offhandStr.size(); index++) {
                insertInv.setInt(4, index);
                insertInv.setString(6, offhandStr.get(index));
                insertInv.executeUpdate();
            }

            PreparedStatement updateInvId = connection.prepareStatement(updateLastInvId);
            updateInvId.setLong(1, id);
            updateInvId.setString(2, player.getUuidAsString());
            updateInvId.setString(3, MongoAuthConfig.config.databaseInfo.serverId);
            updateInvId.executeUpdate();

            PreparedStatement dropOldInv = connection.prepareStatement(dropOldInvSlots);
            dropOldInv.setLong(1, prevInvId);
            dropOldInv.setString(2, MongoAuthConfig.config.databaseInfo.serverId);
            dropOldInv.setString(3, player.getUuidAsString());
            dropOldInv.executeUpdate();
        } catch (SQLException e) {
            logException(e, "storeInv");
        }
    }

    @Override
    public void restoreInv(ServerPlayerEntity player) {
        try (PreparedStatement statement = connection.prepareStatement(getInvData)) {
            long id = getLastInvId(player.getUuid());
            PlayerInventory inventory = player.getInventory();
            LinkedList<String> mainStr = new LinkedList<>();
            LinkedList<String> armorStr = new LinkedList<>();
            LinkedList<String> offhandStr = new LinkedList<>();

            statement.setLong(1, id);
            statement.setString(2, MongoAuthConfig.config.databaseInfo.serverId);
            statement.setString(3, player.getUuidAsString());

            ResultSet set = statement.executeQuery();
            while (set.next()) {
                int index = set.getInt("Slot");
                String value = set.getString("Value");
                (switch (set.getString("Section")) {
                    case "armor" -> armorStr;
                    case "offhand" -> offhandStr;
                    default -> mainStr;
                }).add(index, value);
            }

            MongoDatabaseAccess.deStringify(inventory.main, mainStr);
            MongoDatabaseAccess.deStringify(inventory.armor, armorStr);
            MongoDatabaseAccess.deStringify(inventory.offHand, offhandStr);
        } catch (SQLException e) {
            logException(e, "restoreInv");
        }
    }

    @Override
    public void saveAuthPlayer(ServerPlayerEntity player) {
        try {
            AuthenticationPlayer authPlayer = (AuthenticationPlayer) player;
            if (authPlayerDataExists(player.getUuid())) {
                PreparedStatement update = connection.prepareStatement(updateAuthPlayerData);
                update.setDouble(1, authPlayer.getAuthPos().x);
                update.setDouble(2, authPlayer.getAuthPos().y);
                update.setDouble(3, authPlayer.getAuthPos().z);
                update.setString(4, player.getUuidAsString());
                update.setString(5, MongoAuthConfig.config.databaseInfo.serverId);
                update.executeUpdate();
            } else {
                PreparedStatement insert = connection.prepareStatement(insertAuthPlayerData);
                insert.setString(1, player.getUuidAsString());
                insert.setString(2, MongoAuthConfig.config.databaseInfo.serverId);
                if (authPlayer.getAuthPos() == null) {
                    Vec3d pos = player.getPos();
                    insert.setDouble(3, pos.x);
                    insert.setDouble(4, pos.y);
                    insert.setDouble(5, pos.z);
                } else {
                    insert.setDouble(3, authPlayer.getAuthPos().x);
                    insert.setDouble(4, authPlayer.getAuthPos().y);
                    insert.setDouble(5, authPlayer.getAuthPos().z);
                }
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            logException(e, "saveAuthPlayer");
        }
    }

    @Override
    public void loadAuthPlayer(ServerPlayerEntity player) {
        try {
            PreparedStatement load = connection.prepareStatement(getAuthPlayerData);
            load.setString(1, player.getUuidAsString());
            load.setString(2, MongoAuthConfig.config.databaseInfo.serverId);
            ResultSet set = load.executeQuery();
            if (set.next()) {
                double x = set.getDouble("XPos");
                double y = set.getDouble("YPos");
                double z = set.getDouble("ZPos");
                ((AuthenticationPlayer) player).setAuthPos(new Vec3d(x, y, z));
            }
        } catch (SQLException e) {
            logException(e, "loadAuthPlayer");
        }
    }

    private boolean authPlayerDataExists(UUID uuid) {
        try {
            PreparedStatement statement = connection.prepareStatement(authPlayerDataExists);
            statement.setString(1, uuid.toString());
            statement.setString(2, MongoAuthConfig.config.databaseInfo.serverId);
            return statement.executeQuery().next();
        } catch (SQLException e) {
            logException(e, "authPlayerDataExists");
        }
        return false;
    }

    private void logException(SQLException e, String location) {
        MongoAuth.LOGGER.warn("Exception handlong database in " + location + "!", e);
    }
}
