package io.github.shroompye.mongoauth;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import io.github.shroompye.mongoauth.commands.*;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.mixin.PlayerEntityAccessor;
import io.github.shroompye.mongoauth.util.AuthDataDatabaseAccess;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import io.github.shroompye.mongoauth.util.GlobalsDatabaseAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.gui.FabricGuiEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static io.github.shroompye.mongoauth.config.MongoAuthConfig.DatabaseInfo;

@SuppressWarnings("FieldCanBeLocal")
public class MongoAuth implements ModInitializer {
    public static final String modid = "mongo-auth";
    public static final MongoAuthConfig CONFIG = new MongoAuthConfig();
    public static final Logger LOGGER = LogManager.getLogger();
    public static AuthDataDatabaseAccess playerCache;
    public static GlobalsDatabaseAccessor globals;
    public static final LinkedList<String> onlineUsernames = new LinkedList<>();
    public static String NAME = "";

    private static MongoClient client;
    private static MongoDatabase database;
    private static MongoCollection<Document> authCollection;
    private static MongoCollection<Document> globalsCollection;
    private static MongoCollection<Document> serverSpecificCollection;

    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer(modid).ifPresent(modContainer -> NAME = modContainer.getMetadata().getName());
        CONFIG.readConfigFromFile();
        CONFIG.saveConfigToFile();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            LoginCommand.register(dispatcher);
            LogoutCommand.register(dispatcher);
            RegisterCommand.register(dispatcher);
            RefreshauthCommand.register(dispatcher);
            MongoAuthMainCommand.register(dispatcher);
        });

        readDB();
    }

    private static void readDB() {
        prepDB();
        playerCache = new AuthDataDatabaseAccess(authCollection);
        globals = new GlobalsDatabaseAccessor(globalsCollection);
    }

    private static void prepDB() {
        String[] split = DatabaseInfo.address.getValue().split(":");
        int port = split.length < 2 ? 27017 : Integer.parseInt(split[1]);
        String address = split[0];
        MongoCredential credential = MongoCredential.createCredential(DatabaseInfo.username.getValue(), DatabaseInfo.userSourceDB.getValue(), DatabaseInfo.password.getValue().toCharArray());
        MongoClientSettings settings = MongoClientSettings.builder()
                .credential(credential)
                .applyToSslSettings(builder -> builder.enabled(true))
                .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(address, port))))
                .build();
        client = MongoClients.create(settings);

        try {
            database = client.getDatabase(DatabaseInfo.database.getValue());
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
            serverSpecificCollection = database.getCollection("server-" + DatabaseInfo.serverId.getValue());
        } catch (IllegalArgumentException e) {
            database.createCollection("server-" + DatabaseInfo.serverId.getValue());
            serverSpecificCollection = database.getCollection("server-" + DatabaseInfo.serverId.getValue());
        }
    }

    public static void storeInv(ServerPlayerEntity player) {
        PlayerInventory inventory = ((PlayerEntityAccessor) player).getInventory();
        Document invDoc = new Document();
        invDoc.put("type", "inventory");
        invDoc.put("uuid", player.getUuid().toString());
        invDoc.put("main", inventory.main);
        invDoc.put("armor", inventory.armor);
        invDoc.put("offhand", inventory.offHand);
        serverSpecificCollection.insertOne(invDoc);
    }

    public static void restoreInv(ServerPlayerEntity player) {
        Bson filter = Filters.and(Filters.eq("uuid", player.getUuidAsString()), Filters.eq("type", "inventory"));
        PlayerInventory inventory = ((PlayerEntityAccessor) player).getInventory();
        FindIterable<Document> documents = serverSpecificCollection.find(filter);
        MongoCursor<Document> iterator = documents.iterator();
        if (!iterator.hasNext()) return;
        Document document = iterator.next();
        serverSpecificCollection.deleteMany(filter);
        List<ItemStack> main = document.getList("main", ItemStack.class);
        List<ItemStack> armor = document.getList("armor", ItemStack.class);
        List<ItemStack> offhand = document.getList("offhand", ItemStack.class);
        for (int a = 0; a < inventory.main.size(); a++) {
            inventory.main.set(a, main.get(a));
        }
        for (int a = 0; a < inventory.armor.size(); a++) {
            inventory.armor.set(a, armor.get(a));
        }
        for (int a = 0; a < inventory.offHand.size(); a++) {
            inventory.offHand.set(a, offhand.get(a));
        }
    }

    public static void saveAuthPlayer(ServerPlayerEntity player) {
        Document doc = ((AuthenticationPlayer) player).save();
        doc.put("uuid", player.getUuidAsString());
        doc.put("type", "authPlayer");
        serverSpecificCollection.insertOne(doc);
    }

    public static void loadAuthPlayer(ServerPlayerEntity player) {
        Bson filter = Filters.and(Filters.eq("uuid", player.getUuidAsString()), Filters.eq("type", "authPlayer"));
        FindIterable<Document> documents = serverSpecificCollection.find(filter);
        MongoCursor<Document> iterator = documents.iterator();
        if (!iterator.hasNext()) return;
        Document document = iterator.next();
        serverSpecificCollection.deleteMany(filter);
        ((AuthenticationPlayer) player).load(document);
    }
}
