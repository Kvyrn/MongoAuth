package io.github.shroompye.mongoauth;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import io.github.shroompye.mongoauth.commands.*;
import io.github.shroompye.mongoauth.config.MongoAuthConfig;
import io.github.shroompye.mongoauth.mixin.PlayerEntityAccessor;
import io.github.shroompye.mongoauth.util.AuthDataDatabaseAccess;
import io.github.shroompye.mongoauth.util.AuthenticationPlayer;
import io.github.shroompye.mongoauth.util.GlobalsDatabaseAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.gui.FabricGuiEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import javax.print.attribute.standard.MediaSize;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class MongoAuth implements ModInitializer {
    public static final String modid = "mongo-auth";
    public static final Logger LOGGER = LogManager.getLogger();
    public static AuthDataDatabaseAccess playerCache;
    public static GlobalsDatabaseAccessor globals;
    public static final LinkedList<String> onlineUsernames = new LinkedList<>();
    public static String NAME = "";

    @SuppressWarnings("FieldCanBeLocal")
    private static MongoClient client;
    private static MongoDatabase database;
    private static MongoCollection<Document> authCollection;
    private static MongoCollection<Document> globalsCollection;
    private static MongoCollection<Document> serverSpecificCollection;

    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getModContainer(modid).ifPresent(modContainer -> NAME = modContainer.getMetadata().getName());
        MongoAuthConfig.load();

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (((AuthenticationPlayer) oldPlayer).isAuthenticated()) {
                ((AuthenticationPlayer) newPlayer).sientAuth();
            } else {
                try {
                    //TODO: player respawns at spawn point
                    optionalyHideInvless(newPlayer);
                } catch (Exception e) {
                    logNamedError("Respawn unauth", e);
                    throw e;
                }
            }
        });

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
        String[] split = MongoAuthConfig.config.databaseInfo.address.split(":");
        int port = split.length < 2 ? 27017 : Integer.parseInt(split[1]);
        String address = split[0];
        MongoCredential credential = MongoCredential.createCredential(MongoAuthConfig.config.databaseInfo.username, MongoAuthConfig.config.databaseInfo.userSourceDatabase, MongoAuthConfig.config.databaseInfo.password.toCharArray());
        MongoClientSettings settings = MongoClientSettings.builder()
                .credential(credential)
                .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(new ServerAddress(address, port))))
                .build();
        client = MongoClients.create(settings);

        try {
            database = client.getDatabase(MongoAuthConfig.config.databaseInfo.database);
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
            serverSpecificCollection = database.getCollection("server-" + MongoAuthConfig.config.databaseInfo.serverId);
        } catch (IllegalArgumentException e) {
            database.createCollection("server-" + MongoAuthConfig.config.databaseInfo.serverId);
            serverSpecificCollection = database.getCollection("server-" + MongoAuthConfig.config.databaseInfo.serverId);
        }
    }

    public static void storeInv(ServerPlayerEntity player) {
        PlayerInventory inventory = ((PlayerEntityAccessor) player).getInventory();
        Document invDoc = new Document();
        DefaultedList<String> mainStr = DefaultedList.ofSize(inventory.main.size(), "");
        DefaultedList<String> armorStr = DefaultedList.ofSize(inventory.armor.size(), "");
        DefaultedList<String> offhandStr = DefaultedList.ofSize(inventory.offHand.size(), "");
        stringify(inventory.main, mainStr);
        stringify(inventory.armor, armorStr);
        stringify(inventory.offHand, offhandStr);
        invDoc.put("type", "inventory");
        invDoc.put("uuid", player.getUuid().toString());
        invDoc.put("timestamp", new Date().getTime());
        invDoc.put("main", mainStr);
        invDoc.put("armor", armorStr);
        invDoc.put("offhand", offhandStr);
        serverSpecificCollection.insertOne(invDoc);
    }

    private static void stringify(DefaultedList<ItemStack> items, DefaultedList<String> out) {
        for (int i = 0; i < items.size(); i++) {
            out.set(i, itemStackToString(items.get(i)));
        }
    }

    private static void deStringify(DefaultedList<ItemStack> items, List<String> in) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, stringToItemStack(in.get(i)));
        }
    }

    private static String itemStackToString(ItemStack item) {
        String itemData = item.writeNbt(new NbtCompound()).toString();
        return itemData.replace("'", "$");
    }

    private static ItemStack stringToItemStack(String nbt) {
        NbtCompound tag = null;
        try {
            tag = StringNbtReader.parse(nbt.replace("$", "'"));
        } catch (CommandSyntaxException e) {
            logNamedError("Reding item stack", e);
        }
        return ItemStack.fromNbt(tag);
    }

    public static void restoreInv(ServerPlayerEntity player) {
        Bson filter = Filters.and(Filters.eq("uuid", player.getUuidAsString()), Filters.eq("type", "inventory"));
        PlayerInventory inventory = ((PlayerEntityAccessor) player).getInventory();
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

    public static void saveAuthPlayer(ServerPlayerEntity player) {
        serverSpecificCollection.deleteMany(Filters.and(Filters.eq("uuid", player.getUuidAsString()), Filters.eq("type", "authPlayer")));
        Document doc = ((AuthenticationPlayer) player).save();
        doc.put("uuid", player.getUuidAsString());
        doc.put("type", "authPlayer");
        doc.put("timestamp", new Date().getTime());
        serverSpecificCollection.insertOne(doc);
    }

    public static void loadAuthPlayer(ServerPlayerEntity player) {
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

    public static void logNamed(String str) {
        LOGGER.info("[" + NAME + "] " + str);
    }

    public static void logNamedError(String str, Exception e) {
        LOGGER.error("[" + NAME + "] " + str, e);
    }

    public static boolean playerForcedOffline(String name) {
        for (String s : MongoAuthConfig.config.auth().offlineNames) {
            if (name.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    public static void optionalyHide(ServerPlayerEntity player) {
        optionalyHideInvless(player);
        if (MongoAuthConfig.config.privacy.hideInventory) {
            MongoAuth.storeInv(player);
            ((PlayerEntityAccessor) player).getInventory().clear();
        }
    }

    public static void optionalyHideInvless(ServerPlayerEntity player) {
        AuthenticationPlayer authPlayer = (AuthenticationPlayer) player;
        if (player.hasVehicle()) {
            player.getRootVehicle().setNoGravity(true);
            player.getRootVehicle().setInvulnerable(true);
        }
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.setInvisible(true);
        if (MongoAuthConfig.config.privacy.hidePosition) {
            authPlayer.setAuthPos(player.getPos());
            saveAuthPlayer(player);
            player.requestTeleport(0.5d, MongoAuthConfig.config.privacy.hiddenYLevel, 0.5d);
        }
    }
}
