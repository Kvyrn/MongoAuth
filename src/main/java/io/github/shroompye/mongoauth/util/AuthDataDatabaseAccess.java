package io.github.shroompye.mongoauth.util;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.HashMap;
import java.util.UUID;

@SuppressWarnings("unused")
public class AuthDataDatabaseAccess {
    public final HashMap<UUID, AuthData> cachedData = new HashMap<>();
    private final MongoCollection<Document> authCollection;

    public AuthDataDatabaseAccess(MongoCollection<Document> authCollection) {
        this.authCollection = authCollection;
    }

    public AuthData getOrCreate(UUID uuid) {
        if (dataExists(uuid, true)) return cachedData.get(uuid);
        AuthData authData = new AuthData(uuid);
        cachedData.put(uuid, authData);
        authCollection.insertOne(authData.asDocument());
        return authData;
    }

    public void save(AuthData data) {
        Document doc = data.asDocument();
        boolean inexistent = authCollection.findOneAndReplace(Filters.eq("uuid", data.uuid.toString()), doc) == null;
        if (inexistent) authCollection.insertOne(doc);
    }

    public void deleteEntry(UUID uuid) {
        if (!dataExists(uuid, false)) return;
        cachedData.remove(uuid);
        authCollection.deleteMany(Filters.eq("uuid", uuid.toString()));
    }

    public void refreshEntry(UUID uuid) {
        cachedData.remove(uuid);
    }

    public boolean dataExists(UUID uuid, boolean cacheFound) {
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

    public void clearCache() {
        cachedData.clear();
    }
}
