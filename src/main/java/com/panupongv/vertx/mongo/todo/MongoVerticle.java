package com.panupongv.vertx.mongo.todo;

import org.bson.types.ObjectId;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class MongoVerticle extends AbstractVerticle {

    private static final int SETUP_TIMEOUT = 10000;

    public static final String CHECK_USER_EXIST = "com.panupongv.vertx-todo.check_user_exist";
    public static final String CREATE_USER = "com.panupongv.vertx-todo.create_user";
    public static final String ADD_ITEM = "com.panupongv.vertx-todo.add_item";
    public static final String LIST_ITEMS_BY_DUE_DATE = "com.panupongv.vertx-todo.list_items_by_due_date";
    public static final String LIST_ITEMS_BY_PRIORITY = "com.panupongv.vertx-todo.list_items_by_priority";
    public static final String GET_ITEM = "com.panupongv.vertx-todo.get_item";
    public static final String EDIT_ITEM = "com.panupongv.vertx-todo.edit_item";
    public static final String DELETE_ITEM = "com.panupongv.vertx-todo.delete_item";

    public static final String REPLY_STATUS_CODE_KEY = "statusCode";
    public static final String REPLY_CONTENT_KEY = "content";

    private static final String COLLECTION_NAME = "vertx_mongo_todos";
    private static final String MONGO_ID_KEY = "_id";
    private static final String USERNAME_KEY = "username";
    private static final String ITEMS_KEY = "items";
    private static final String ITEM_KEY = "item";

    private enum SortOption {
        BY_DATE,
        BY_PRIORITY
    }

    private MongoClient mongoClient;

    @Override
    public void start(Promise<Void> start) {
        vertx.setTimer(SETUP_TIMEOUT, duration -> {
            start.tryFail(new Throwable(
                    String.format("Mongo Client Setup exceeded timeout of %d milliseconds", SETUP_TIMEOUT)));
        });
        configureMongoDbClient()
                .compose(this::configureEventBusConsumers)
                .onFailure(start::tryFail)
                .onComplete(start::handle);
    }

    private Future<Void> configureMongoDbClient() {
        try {
            String uri = "mongodb://localhost:27017";
            String db = "test-vertx";

            JsonObject mongoconfig = new JsonObject()
                    .put("connection_string", uri)
                    .put("db_name", db);

            mongoClient = MongoClient.createShared(vertx, mongoconfig);

            return mongoClient.runCommand("ping", new JsonObject().put("ping", 1)).mapEmpty();
        } catch (Exception e) {
            return Future.failedFuture(e.getMessage());
        }
    }

    private Future<Void> configureEventBusConsumers(Void unused) {
        Handler<Message<Object>> listItemsByDueDate = (Message<Object> msg) -> this.listItems(SortOption.BY_DATE, msg);
        Handler<Message<Object>> listItemsByPriority = (Message<Object> msg) -> this.listItems(SortOption.BY_PRIORITY,
                msg);

        vertx.eventBus().consumer(CHECK_USER_EXIST).handler(this::checkUserExists);
        vertx.eventBus().consumer(CREATE_USER).handler(this::createUser);
        vertx.eventBus().consumer(ADD_ITEM).handler(this::addItem);
        vertx.eventBus().consumer(LIST_ITEMS_BY_DUE_DATE).handler(listItemsByDueDate);
        vertx.eventBus().consumer(LIST_ITEMS_BY_PRIORITY).handler(listItemsByPriority);
        vertx.eventBus().consumer(GET_ITEM).handler(this::getItem);
        vertx.eventBus().consumer(EDIT_ITEM).handler(this::editItem);
        vertx.eventBus().consumer(DELETE_ITEM).handler(this::deleteItem);

        return Future.succeededFuture();
    }

    public static JsonObject addItemMessage(String username, JsonObject itemJson) {
        return new JsonObject()
                .put(USERNAME_KEY, username)
                .put(ITEM_KEY, itemJson);
    }

    public static JsonObject getItemMessage(String username, String itemId) {
        return new JsonObject()
                .put(USERNAME_KEY, username)
                .put(Item.MONGO_ID_KEY, itemId);
    }

    public static JsonObject editItemMessage(String username, Item item) {
        return new JsonObject()
                .put(USERNAME_KEY, username)
                .put(ITEM_KEY, item.getMongoDbJson());
    }

    public static JsonObject deleteItemMessage(String username, String itemId) {
        return new JsonObject()
                .put(USERNAME_KEY, username)
                .put(Item.MONGO_ID_KEY, itemId);
    }

    public static JsonObject resultJson(int statusCode, Object content) {
        return new JsonObject()
                .put(REPLY_STATUS_CODE_KEY, statusCode)
                .put(REPLY_CONTENT_KEY, content);
    }

    private void checkUserExists(Message<Object> msg) {
        String username = (String) msg.body();
        JsonObject findUser = new JsonObject().put(USERNAME_KEY, username);

        mongoClient.find(COLLECTION_NAME, findUser)
                .onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        msg.reply(asyncResult.result().size() >= 1);
                    } else {
                        msg.fail(500, asyncResult.cause().getMessage());
                    }
                }).onFailure(throwable -> {
                    msg.fail(500, throwable.getMessage());
                });
    }

    private void createUser(Message<Object> msg) {
        String username = (String) msg.body();
        JsonObject json = new JsonObject().put(USERNAME_KEY, username).put(ITEMS_KEY, new JsonArray());

        mongoClient.save(COLLECTION_NAME, json)
                .onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        msg.reply(resultJson(200, String.format("User '%s' created", username)));
                    } else {
                        msg.reply(resultJson(500, asyncResult.cause().getMessage()));
                    }
                }).onFailure(throwable -> {
                    msg.reply(resultJson(500, throwable.getMessage()));
                });
    }

    private void addItem(Message<Object> msg) {
        JsonObject inputJson = (JsonObject) msg.body();
        String username = inputJson.getString(USERNAME_KEY);
        JsonObject newItem = inputJson.getJsonObject(ITEM_KEY).put(Item.MONGO_ID_KEY, new ObjectId().toString());

        JsonObject query = new JsonObject().put(USERNAME_KEY, username);
        JsonObject update = new JsonObject().put("$push", new JsonObject().put(ITEMS_KEY, newItem));

        mongoClient.updateCollection(COLLECTION_NAME, query,
                update).onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        msg.reply(resultJson(200, "Item added"));
                    } else {
                        msg.reply(resultJson(500, asyncResult.cause().getMessage()));
                    }
                });
    }

    private void listItems(SortOption sortOption, Message<Object> msg) {
        String username = (String) msg.body();

        JsonObject findUser = matchUsernameClause(username);
        JsonObject unwindItems = unwindItemsClause();
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
            JsonArray items = data.getJsonArray(ITEMS_KEY);
            if (items == null) {
                msg.reply(resultJson(500, "Internal Server Error: Wrong aggregation result format"));
                return;
            }
            msg.reply(resultJson(200, data.getJsonArray(ITEMS_KEY)));
        });

    }

    private void getItem(Message<Object> msg) {
        JsonObject inputJson = (JsonObject) msg.body();
        String username = inputJson.getString(USERNAME_KEY);
        String itemId = inputJson.getString(Item.MONGO_ID_KEY);

        JsonObject findUser = matchUsernameClause(username);

        JsonObject unwindItems = unwindItemsClause();

        JsonObject findItem = new JsonObject().put(
                "$match",
                new JsonObject()
                        .put(ITEMS_KEY + "." + Item.MONGO_ID_KEY, itemId));

        JsonArray pipeline = new JsonArray()
                .add(findUser)
                .add(unwindItems)
                .add(findItem);

        mongoClient.aggregate(COLLECTION_NAME, pipeline).handler(data -> {
            JsonObject itemWithDetails = data.getJsonObject(ITEMS_KEY);
            if (itemWithDetails == null) {
                msg.reply(resultJson(500, "Internal Server Error: Wrong aggregation result format"));
                return;
            }
            msg.reply(resultJson(200, itemWithDetails));
        }).endHandler(readStream -> {
            msg.reply(resultJson(400, String.format("Cannot find item with ID '%s'", itemId)));
        });
    }

    private void editItem(Message<Object> msg) {
        JsonObject inputJson = (JsonObject) msg.body();
        String username = inputJson.getString(USERNAME_KEY);
        JsonObject item = inputJson.getJsonObject(ITEM_KEY);

        JsonObject query = findItemUnderUser(username, item.getString(MONGO_ID_KEY));

        JsonObject update = new JsonObject().put(
                "$set", new JsonObject().put(ITEMS_KEY + ".$", item));

        mongoClient.updateCollection(
                COLLECTION_NAME, query, update).onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        if (asyncResult.result().getDocMatched() == 0) {
                            msg.reply(resultJson(400, String.format("Cannot find item with ID '%s'",
                                    item.getString(Item.MONGO_ID_KEY))));
                            return;
                        }
                        if (asyncResult.result().getDocModified() == 0) {
                            msg.reply(resultJson(400, "No changes"));
                            return;
                        }
                        msg.reply(resultJson(200, "Item updated"));
                    } else {
                        msg.reply(resultJson(500, asyncResult.cause().toString()));
                    }
                });

    }

    private void deleteItem(Message<Object> msg) {
        JsonObject inputJson = (JsonObject) msg.body();
        String username = inputJson.getString(USERNAME_KEY);
        String itemId = inputJson.getString(MONGO_ID_KEY);

        JsonObject query = findItemUnderUser(username, itemId);

        JsonObject update = new JsonObject()
                .put("$pull", new JsonObject().put(ITEMS_KEY,
                        new JsonObject().put(Item.MONGO_ID_KEY, itemId)));

        mongoClient.updateCollection(COLLECTION_NAME, query,
                update).onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        if (asyncResult.result().getDocMatched() == 0) {
                            msg.reply(resultJson(400,
                                    String.format("Cannot find item with ID '%s'",
                                            itemId)));
                            return;
                        }
                        if (asyncResult.result().getDocModified() == 0) {
                            msg.reply(resultJson(400, "No item removed"));
                            return;
                        }
                        msg.reply(resultJson(200, "Item removed"));

                    } else {
                        msg.reply(resultJson(400, asyncResult.cause().getMessage()));
                    }
                });

    }

    private JsonObject matchUsernameClause(String username) {
        return new JsonObject()
                .put("$match", new JsonObject().put(USERNAME_KEY, username));
    }

    private JsonObject unwindItemsClause() {
        return new JsonObject().put("$unwind", "$" + ITEMS_KEY);
    }

    private JsonObject findItemUnderUser(String username, String itemId) {
        return new JsonObject()
                .put(USERNAME_KEY, username)
                .put(ITEMS_KEY + "." + Item.MONGO_ID_KEY, itemId);
    }
}