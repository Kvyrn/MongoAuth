package io.github.shroompye.mongoauth.database;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.util.AuthData;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import io.github.shroompye.mongoauth.util.ItemStachHelper;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

import static io.github.shroompye.mongoauth.MongoAuth.*;
import static io.github.shroompye.mongoauth.util.AuthData.ARGON2;

public class MongoDatabaseAccess implements IDatabaseAccess {
    private MongoCollection<Document> serverSpecificCollection;
    private MongoCollection<Document> globalsCollection;
    private MongoCollection<Document> authCollection;
    private MongoDatabase database;
    private final HashMap<UUID, AuthData> cachedData = new HashMap<>();

    public MongoDatabaseAccess() {
        String[] split = MongoAuthConfig.CONFIG.databaseInfo.mongoDB.address.split(":");
        int port = split.length < 2 ? 27017 : Integer.parseInt(split[1]);
        String address = split[0];
        MongoCredential credential = MongoCredential.createCredential(MongoAuthConfig.CONFIG.databaseInfo.mongoDB.username, MongoAuthConfig.CONFIG.databaseInfo.mongoDB.userSourceDatabase, MongoAuthConfig.CONFIG.databaseInfo.mongoDB.password.toCharArray());
        MongoClientSettings settings = MongoClientSettings.builder()
                .credential(credential)
                .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(new ServerAddress(address, port))))
                .build();
        MongoClient client = MongoClients.create(settings);

        try {
            database = client.getDatabase(MongoAuthConfig.CONFIG.databaseInfo.mongoDB.database);
        } catch (IllegalArgumentException e) {
            LOGGER.fatal("[" + NAME + "] Invalid MongoDB database!", e);
            FabricGuiEntry.displayCriticalError(e, true);
        }

        try {
            authCollection = database.getCollection("passwords");
        } catch (IllegalArgumentException e) {
            database.createCollection("passwords");
            authCollection = database.getCollection("passwords");
        }

        try {
            globalsCollection = database.getCollection("globals");
        } catch (IllegalArgumentException e) {
            database.createCollection("globals");
            globalsCollection = database.getCollection("globals");
        }

        try {
            serverSpecificCollection = database.getCollection("server-" + MongoAuthConfig.CONFIG.databaseInfo.serverId);
        } catch (IllegalArgumentException e) {
            database.createCollection("server-" + MongoAuthConfig.CONFIG.databaseInfo.serverId);
            serverSpecificCollection = database.getCollection("server-" + MongoAuthConfig.CONFIG.databaseInfo.serverId);
        }
    }

    @Override
    public boolean verifyGlobalPassword(String password) {
        String hash = getGlobalPasswordHash();
        if (hash.length() <= 0) return true;
        return ARGON2.verify(hash, password.toCharArray());
    }

    private String getGlobalPasswordHash() {
        FindIterable<Document> documents = globalsCollection.find(Filters.eq("key", "globalPassword"));
        MongoCursor<Document> iterator = documents.iterator();
        if (iterator.hasNext()) {
            Document doc = iterator.next();
            return doc.getString("value");
        } else {
            return "";
        }
    }

    @Override
    public void setGlobalPassword(String password) {
        String hash = AuthData.createHash(password);
        FindIterable<Document> documents = globalsCollection.find(Filters.eq("key", "globalPassword"));
        MongoCursor<Document> iterator = documents.iterator();
        if (iterator.hasNext()) {
            Document doc = iterator.next();
            doc.put("value", hash);

            globalsCollection.updateOne(Filters.eq("key", "globalPassword"), doc);
        } else {
            Document doc = new Document("key", "globalPassword");
            doc.put("value", hash);
            globalsCollection.insertOne(doc);
        }
    }

    @Override
    public AuthData getOrCreateAuthData(UUID uuid) {
        if (authDataExists(uuid, true)) return cachedData.get(uuid);
        AuthData authData = new AuthData(uuid);
        cachedData.put(uuid, authData);
        authCollection.insertOne(authData.asDocument());
        return authData;
    }

    @Override
    public void saveAuthData(AuthData authData) {
        Document doc = authData.asDocument();
        boolean inexistent = authCollection.findOneAndReplace(Filters.eq("uuid", authData.uuid.toString()), doc) == null;
        if (inexistent) authCollection.insertOne(doc);
    }

    @Override
    public void deleteAuthData(UUID uuid) {
        if (!authDataExists(uuid, false)) return;
        cachedData.remove(uuid);
        authCollection.deleteMany(Filters.eq("uuid", uuid.toString()));
    }

    @Override
    public void refreshAuthData(UUID uuid) {
        cachedData.remove(uuid);
    }

    @Override
    public boolean authDataExists(UUID uuid, boolean cacheFound) {
        if (cachedData.containsKey(uuid)) {
            return true;
        }
        FindIterable<Document> documents = authCollection.find(Filters.eq("uuid", uuid.toString()));
        if (cacheFound) {
            for (Document doc : documents) {
                AuthData data = AuthData.fromDocument(doc);
                cachedData.put(data.uuid, data);
            }
        }
        return documents.iterator().hasNext();
    }

    @Override
    public void clearAuthDataCache() {
        cachedData.clear();
    }

    @Override
    public void storeInv(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        Document invDoc = new Document();
        DefaultedList<String> mainStr = DefaultedList.ofSize(inventory.main.size(), "");
        DefaultedList<String> armorStr = DefaultedList.ofSize(inventory.armor.size(), "");
        DefaultedList<String> offhandStr = DefaultedList.ofSize(inventory.offHand.size(), "");
        stringify(inventory.main, mainStr);
        stringify(inventory.armor, armorStr);
        stringify(inventory.offHand, offhandStr);
        System.out.println(inventory.main.size());
        System.out.println(inventory.armor.size());
        System.out.println(inventory.offHand.size());
        invDoc.put("type", "inventory");
        invDoc.put("uuid", player.getUuid().toString());
        invDoc.put("timestamp", new Date().getTime());
        invDoc.put("main", mainStr);
        invDoc.put("armor", armorStr);
        invDoc.put("offhand", offhandStr);
        serverSpecificCollection.insertOne(invDoc);
    }

    @Override
    public void restoreInv(ServerPlayerEntity player) {
        Bson filter = Filters.and(Filters.eq("uuid", player.getUuidAsString()), Filters.eq("type", "inventory"));
        PlayerInventory inventory = player.getInventory();
        FindIterable<Document> documents = serverSpecificCollection.find(filter).sort(Sorts.descending("timestamp"));
        MongoCursor<Document> iterator = documents.iterator();
        if (!iterator.hasNext()) return;
        Document document = iterator.next();
        serverSpecificCollection.deleteMany(filter);
        List<String> main = document.getList("main", String.class);
        List<String> armor = document.getList("armor", String.class);
        List<String> offhand = document.getList("offhand", String.class);
        deStringify(inventory.main, main);
        deStringify(inventory.armor, armor);
        deStringify(inventory.offHand, offhand);
    }

    public static void stringify(DefaultedList<ItemStack> items, DefaultedList<String> out) {
        for (int i = 0; i < items.size(); i++) {
            out.set(i, ItemStachHelper.itemStackToString(items.get(i)));
        }
    }

    public static void deStringify(DefaultedList<ItemStack> items, List<String> in) {
        for (int i = 0; i < in.size(); i++) {
            items.set(i, ItemStachHelper.stringToItemStack(in.get(i)));
        }
    }

    @Override
    public void saveAuthPlayer(ServerPlayerEntity player) {
        serverSpecificCollection.deleteMany(Filters.and(Filters.eq("uuid", player.getUuidAsString()), Filters.eq("type", "authPlayer")));
        Document doc = ((AuthenticationPlayer) player).save();
        doc.put("uuid", player.getUuidAsString());
        doc.put("type", "authPlayer");
        doc.put("timestamp", new Date().getTime());
        serverSpecificCollection.insertOne(doc);
    }

    @Override
    public void loadAuthPlayer(ServerPlayerEntity player) {
        try {
            Bson filter = Filters.and(Filters.eq("uuid", player.getUuidAsString()), Filters.eq("type", "authPlayer"));
            FindIterable<Document> documents = serverSpecificCollection.find(filter).sort(Sorts.descending("timestamp"));
            MongoCursor<Document> iterator = documents.iterator();
            if (!iterator.hasNext()) return;
            Document document = iterator.next();
            serverSpecificCollection.deleteMany(filter);
            ((AuthenticationPlayer) player).load(document);
        } catch (Exception e) {
            logNamedError("Loading player data", e);
        }
    }
}
