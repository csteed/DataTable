package data;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * Created by csg on 4/14/15.
 */

public class MongoTest {
    public static void main (String args[]) throws Exception {
        MongoClient connection = new MongoClient();
        MongoDatabase carsDB = connection.getDatabase("cars");

        MongoCollection<Document> carsCollection = carsDB.getCollection("cars");

        MongoCursor<Document> cursor = carsCollection.find().iterator();
        while (cursor.hasNext()) {
            try {
                System.out.println(cursor.next().getDouble("MPG"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        cursor.close();
    }
}
