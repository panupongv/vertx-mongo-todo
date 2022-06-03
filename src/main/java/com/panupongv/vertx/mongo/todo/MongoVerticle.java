package com.panupongv.vertx.mongo.todo;

import java.util.Date;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class MongoVerticle extends AbstractVerticle {

    @Override
    public void start() {
        String uri = "mongodb://localhost:27017";
        String db = "test-vertx";

        JsonObject mongoconfig = new JsonObject()
            .put("connection_string", uri)
            .put("db_name", db);

        MongoClient mongoClient = MongoClient.createShared(vertx, mongoconfig);

        Item item = new Item("Item name", "Some description", new Date(), 1178);
        
        mongoClient.save(Item.COLLECTION_NAME, item.getMongoDbJson())
        .compose(id -> {
            System.out.println("Inserted id: " + id);
            return mongoClient.find("products", new JsonObject().put("itemId", "12345"));
          })
        //  .compose(res -> {
        //    System.out.println("Name is " + res.get(0).getString("name"));
        //    return mongoClient.removeDocument("products", new JsonObject().put("itemId", "12345"));
        //  })
          .onComplete(ar -> {
            if (ar.succeeded()) {
              System.out.println("Done ");
            } else {
              ar.cause().printStackTrace();
            }
          });

    }
}