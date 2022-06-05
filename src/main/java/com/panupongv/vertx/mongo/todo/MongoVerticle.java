package com.panupongv.vertx.mongo.todo;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
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
    private static final String MONGO_ID_KEY = "_id";
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
        Handler<Message<Object>> listItemsByDueDate = (Message<Object> msg) -> this.listItems(SortOption.BY_DATE, msg);
        Handler<Message<Object>> listItemsByPriority = (Message<Object> msg) -> this.listItems(SortOption.BY_PRIORITY,
                msg);

        vertx.eventBus().consumer(CREATE_USER).handler(this::createUser);
        vertx.eventBus().consumer(ADD_ITEM).handler(this::addItem);
        vertx.eventBus().consumer(LIST_ITEMS_BY_DUE_DATE).handler(listItemsByDueDate);
        vertx.eventBus().consumer(LIST_ITEMS_BY_PRIORITY).handler(listItemsByPriority);

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
                                .convertJsonQuotes(
                                        String.format("{'%s': '%s', '%s': []}", USERNAME_KEY, username, ITEMS_KEY));
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

    private void addItem(Message<Object> msg) {

        JsonObject inputJson = (JsonObject) msg.body();
        String username = inputJson.getString(USERNAME_KEY);
        JsonObject newItem = inputJson.getJsonObject(ITEM_KEY);

        String queryJsonString = Utils.convertJsonQuotes(String.format("{'%s': '%s'}", USERNAME_KEY, username));
        JsonObject query = new JsonObject(queryJsonString);

        String updateJsonString = Utils.convertJsonQuotes(
                String.format("{'$push': {'%s': %s}}", ITEMS_KEY, newItem.toString()));
        JsonObject update = new JsonObject(updateJsonString);

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

    private void listItems(SortOption sortOption, Message<Object> msg) { // User name, sort options?
        String username = (String) msg.body();

        userExists(username).onComplete(userExistsAsyncResult -> {
            if (userExistsAsyncResult.result()) {
                JsonObject findUser = new JsonObject()
                        .put("$match", new JsonObject().put(USERNAME_KEY, username));

                JsonObject unwindItems = new JsonObject().put("$unwind", "$" + ITEMS_KEY);

                JsonObject excludeDescription = new JsonObject().put("$project", new JsonObject()
                        .put(MONGO_ID_KEY, 1)
                        .put(USERNAME_KEY, 0)
                        .put(ITEMS_KEY, new JsonObject().put(Item.DESCRIPTION_KEY, 0)));

                JsonObject sortItemsOption = new JsonObject().put(ITEMS_KEY + "." + Item.DUE_DATE_KEY, 1);
                switch (sortOption) {
                    case BY_DATE:
                        break;
                    case BY_PRIORITY:
                        sortItemsOption = new JsonObject().put(ITEMS_KEY + "." + Item.PRIORITY_KEY, -1);
                        break;
                }
                JsonObject sortItems = new JsonObject().put("$sort", sortItemsOption);

                JsonObject groupIntoUser = new JsonObject().put("$group",
                        new JsonObject()
                                .put(Item.MONGO_ID_KEY, "$_id")
                                .put(ITEMS_KEY, new JsonObject().put("$push", "$" + ITEMS_KEY)));

                JsonArray pipeline = new JsonArray()
                        .add(findUser)
                        .add(unwindItems)
                        .add(excludeDescription)
                        .add(sortItems)
                        .add(groupIntoUser);

                mongoClient.aggregate(COLLECTION_NAME, pipeline).handler(data -> {
                    msg.reply(data.getJsonArray(ITEMS_KEY));
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