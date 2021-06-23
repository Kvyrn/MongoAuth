package io.github.shroompye.mongoauth.util;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import static io.github.shroompye.mongoauth.util.AuthData.ARGON_2;

@SuppressWarnings("ClassCanBeRecord")
public class GlobalsDatabaseAccessor {
    private final MongoCollection<Document> collection;

    public GlobalsDatabaseAccessor(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public boolean verifyGlobalPassword(String password) {
        String hash = getGlobalPasswordHash();
        if (hash.length() <= 0) return true;
        return ARGON_2.verify(hash, password);
    }

    public void setGlobalPassword(String password) {
        String hash = AuthData.createHash(password);
        FindIterable<Document> documents = collection.find(Filters.eq("key", "globalPassword"));
        MongoCursor<Document> iterator = documents.iterator();
        if (iterator.hasNext()) {
            Document doc = iterator.next();
            doc.put("value", hash);

            collection.updateOne(Filters.eq("key", "globalPassword"), doc);
        } else {
            Document doc = new Document("key", "globalPassword");
            doc.put("value", hash);
            collection.insertOne(doc);
        }
    }

    public String getGlobalPasswordHash() {
        FindIterable<Document> documents = collection.find(Filters.eq("key", "globalPassword"));
        MongoCursor<Document> iterator = documents.iterator();
        if (iterator.hasNext()) {
            Document doc = iterator.next();
            return doc.getString("value");
        } else {
            return "";
        }
    }
}
