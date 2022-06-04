package com.panupongv.vertx.mongo.todo;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class MongoVerticle extends AbstractVerticle {

    public static final String CREATE_USER = "com.panupongv.vertx-todo.create_user";
    public static final String ADD_ITEM = "com.panupongv.vertx-todo.add_item";
    public static final String LIST_ITEMS_BY_DUE_DATE = "com.panupongv.vertx-todo.list_items_by_due_date";
    public static final String LIST_ITEMS_BY_PRIORITY = "com.panupongv.vertx-todo.list_items_by_priority";
    public static final String GET_ITEM = "com.panupongv.vertx-todo.get_item";
    public static final String EDIT_ITEM = "com.panupongv.vertx-todo.edit_item";
    public static final String DELETE_ITEM = "com.panupongv.vertx-todo.delete_item";

    private static final String COLLECTION_NAME = "vertx_mongo_todos";
    private static final String USERNAME_KEY = "username";
    private static final String ITEMS_KEY = "items";
    private static final String ITEM_KEY = "items";

    private enum SortOption {
        BY_DATE,
        BY_PRIORITY
    }

    private MongoClient mongoClient;

    @Override
    public void start(Promise<Void> start) {
        configureMongoDbClient()
                .compose(this::configureEventBusConsumers)
                .onComplete(start::handle);
    }

    private Future<Void> configureMongoDbClient() {
        String uri = "mongodb://localhost:27017";
        String db = "test-vertx";

        JsonObject mongoconfig = new JsonObject()
                .put("connection_string", uri)
                .put("db_name", db);

        mongoClient = MongoClient.createShared(vertx, mongoconfig);

        return Future.succeededFuture();
    }

    private Future<Void> configureEventBusConsumers(Void unused) {
        System.out.println("Consumer Setup");

        vertx.eventBus().consumer(CREATE_USER).handler(this::createUser);
        vertx.eventBus().consumer(ADD_ITEM).handler(this::addItem);

        return Future.succeededFuture();
    }

    public static JsonObject saveItemMessage(String username, Item item) {
        return new JsonObject()
                .put(USERNAME_KEY, username)
                .put(ITEM_KEY, item.getMongoDbJson());
    }

    private void createUser(Message<Object> msg) {
        String username = (String) msg.body();

        userExists(username)
                .onComplete(userExistsAsyncResult -> {
                    boolean userExists = userExistsAsyncResult.result();
                    if (userExists) {
                        msg.fail(400, String.format("User '%s' already exists", username));
                    } else {
                        String jsonString = Utils
                                .convertJsonQuotes(String.format("{'username': '%s', 'items': []}", username));
                        JsonObject json = new JsonObject(jsonString);

                        mongoClient.save(COLLECTION_NAME, json)
                                .onComplete(asyncResult -> {
                                    if (asyncResult.succeeded()) {
                                        msg.reply(String.format("User '%s' created", username));
                                    } else {
                                        msg.fail(500, asyncResult.cause().getMessage());
                                    }
                                });
                    }
                });
    }

    private void listItems(SortOption sortOption, Message<Object> msg) { // User name, sort options?
        String username = (String) msg.body();

    }

    private void addItem(Message<Object> msg) {

        JsonObject inputJson = (JsonObject) msg.body();
        String username = inputJson.getString(USERNAME_KEY);
        JsonObject newItem = inputJson.getJsonObject(ITEM_KEY);

        String queryJsonString = Utils.convertJsonQuotes(String.format("{'username': '%s'}", username));
        JsonObject query = new JsonObject(queryJsonString);
        System.out.println(query);

        String updateJsonString = Utils.convertJsonQuotes(String.format(
                "{'$push': {'items': %s}}",
                newItem.toString()));
        JsonObject update = new JsonObject(updateJsonString);
        System.out.println(update);

        userExists(username).onComplete(userExistsAsyncResult -> {
            if (userExistsAsyncResult.result()) {
                mongoClient.updateCollection(COLLECTION_NAME, query, update).onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        msg.reply("Item added");
                    } else {
                        msg.fail(500, asyncResult.cause().getMessage());
                    }
                });
            } else {
                msg.fail(400, String.format("User '%s' not found", username));
            }
        });
    }

    private Future<Boolean> userExists(String username) {
        String findUserJsonString = Utils.convertJsonQuotes(String.format("{'username': '%s'}", username));
        JsonObject findUserJson = new JsonObject(findUserJsonString);

        return mongoClient.find(COLLECTION_NAME, findUserJson).map(users -> users.size() == 1);
    }
}