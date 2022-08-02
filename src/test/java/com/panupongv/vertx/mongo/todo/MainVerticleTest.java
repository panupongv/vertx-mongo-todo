package com.panupongv.vertx.mongo.todo;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.reactiverse.junit5.web.WebClientOptionsInject;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import static io.reactiverse.junit5.web.TestRequest.*;

@ExtendWith({
        VertxExtension.class, // VertxExtension MUST be configured before VertxWebClientExtension
})
public class MainVerticleTest {
    final int TEST_PORT = 7777;
    String deploymentId;

    Vertx vertx = Vertx.vertx();
    VertxTestContext testContext = new VertxTestContext();

    @WebClientOptionsInject
    WebClient client = WebClient.create(vertx);

    private static final String TEST_DB_NAME = "todo-app-test";
    private static MongoTestUtil mongoTestUtil;

    private static class MongoTestUtil {

        private static final String COLLECTION_NAME = "items";
        private MongoClient mongoClient;

        MongoTestUtil(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
        }

        public void cleanDb() {
            mongoClient.removeDocument(COLLECTION_NAME, new JsonObject());
        }

        public Future<Void> insertUser(String username) {
            return mongoClient.save(COLLECTION_NAME,
                    new JsonObject()
                            .put("_id", new ObjectId().toString())
                            .put("username", username)
                            .put("items", new JsonArray()))
                    .mapEmpty();
        }
    }

    private String createUserUrl = "/api/v1/users";

    private String addItemUrl(String username) {
        return String.format("/api/v1/users/%s/items", username);
    }

    @BeforeAll
    public static void initialSetup(Vertx vertx, VertxTestContext testContext) {
        System.out.println("Initial set up");

        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", TEST_DB_NAME);
        mongoTestUtil = new MongoTestUtil(MongoClient.create(vertx, mongoConfig));
        testContext.completeNow();

        ;
    }

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        System.out.println("Set up");

        testContext
                .assertComplete(vertx.deployVerticle(MainVerticle.class.getName()))
                .onComplete(ar -> {
                    deploymentId = ar.result();
                    mongoTestUtil.cleanDb();
                    testContext.completeNow();
                });
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        System.out.println("Tear down");

        testContext
                .assertComplete(vertx.undeploy(deploymentId))
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    public void testCreateUser(Vertx vertx, VertxTestContext testContext) {
        testRequest(client.post(TEST_PORT, "localhost", createUserUrl))
                .expect(
                        statusCode(200),
                        bodyResponse(Buffer.buffer("User 'newuser' created"), null))
                .sendJson(new JsonObject().put("username", "newuser"), testContext);
    }

    @Test
    public void testCreateUserWithInvalidUsernames(Vertx vertx, VertxTestContext testContext) {
        testRequest(client.post(TEST_PORT, "localhost", createUserUrl))
                .expect(
                        statusCode(400),
                        bodyResponse(Buffer.buffer("Missing JSON request body"), null))
                .send(testContext);

        testRequest(client.post(TEST_PORT, "localhost", createUserUrl))
                .expect(statusCode(400),
                        bodyResponse(Buffer.buffer("A username must be provided"), null))
                .sendJson(new JsonObject().put("random_key", "random value"), testContext);

        testRequest(client.post(TEST_PORT, "localhost", createUserUrl))
                .expect(
                        statusCode(400),
                        bodyResponse(Buffer.buffer(
                                "Username must contain only alphabets and numbers, and must be between %d and %d characters long"),
                                null))
                .sendJson(new JsonObject().put("username", ""), testContext);

        testRequest(client.post(TEST_PORT, "localhost", createUserUrl))
                .expect(
                        statusCode(400),
                        bodyResponse(Buffer.buffer(
                                "Username must contain only alphabets and numbers, and must be between %d and %d characters long"),
                                null))
                .sendJson(new JsonObject().put("username", StringUtils.repeat("x", 40)), testContext);

    }

    @Test
    public void testCreateUserWithExistingUsername(Vertx vertx, VertxTestContext testContext) {
        String username = "existinguser";
        mongoTestUtil.insertUser(username).onComplete(x -> {
            testRequest(client.post(TEST_PORT, "localhost", createUserUrl))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer("User 'existinguser' already exists"), null))
                    .sendJson(new JsonObject().put("username", username), testContext);
        });
    }

    @Test
    public void testAddItem(Vertx vertx, VertxTestContext testContext) {
        String username = "addItemUser";

        mongoTestUtil.insertUser(username).onComplete(x -> {
            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(200),
                            bodyResponse(Buffer.buffer("Item added"), null))
                    .sendJson(new JsonObject()
                            .put(Item.NAME_KEY, "Item Name")
                            .put(Item.DESCRIPTION_KEY, "Item Description")
                            .put(Item.DUE_DATE_KEY, "2022-07-08")
                            .put(Item.PRIORITY_KEY, 100), testContext);
        });
    }

    @Test
    public void testAddItemWithoutUser(Vertx vertx, VertxTestContext testContext) {
        String username = "addItemUser";
        testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                .expect(
                        statusCode(400),
                        bodyResponse(Buffer.buffer("User 'addItemUser' does not exist"), null))
                .sendJson(new JsonObject()
                        .put(Item.NAME_KEY, "Item Name")
                        .put(Item.DESCRIPTION_KEY, "Item Description")
                        .put(Item.DUE_DATE_KEY, "2022-07-08")
                        .put(Item.PRIORITY_KEY, 100), testContext);
    }

    @Test
    public void testAddItemWithoutItemBody(Vertx vertx, VertxTestContext testContext) {
        String username = "addItemUser";
        mongoTestUtil.insertUser(username).onComplete(x -> {
            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer("Missing request body"), null))
                    .send(testContext);
        });
    }

    @Test
    public void testAddItemWithInvalidItemBody(Vertx vertx, VertxTestContext testContext) {
        String username = "addItemUser";
        mongoTestUtil.insertUser(username).onComplete(x -> {

            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer("Invalid input field 'some_key'"), null))
                    .sendJson(new JsonObject().put("some_key", "some value"),
                            testContext);

            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer(
                                    "Missing item name, please use the key 'name'\n" +
                                            "Missing item description, please use the key 'description'\n" +
                                            "Missing item due date, please use the key 'due_date'\n" +
                                            "Missing item priority, please use the key 'priority'"),
                                    null))
                    .sendJson(new JsonObject(), testContext);

            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer(
                                    "Item name must be a string\n" +
                                            "Item description must be a string\n" +
                                            "Item due date must be a string\n" +
                                            "Item priority must be an integer"),
                                    null))
                    .sendJson(new JsonObject()
                            .put("name", 99)
                            .put("description", 100)
                            .put("due_date", 123)
                            .put("priority", "99"), testContext);

            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer("Item name must be a non-empty string with 32 characters limit"),
                                    null))
                    .sendJson(new JsonObject()
                            .put("name", "")
                            .put("description", "Some description")
                            .put("due_date", "2022-01-01")
                            .put("priority", 99), testContext);

            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer(
                                    "Item due date must be a valid YYYY-MM-DD format date"),
                                    null))
                    .sendJson(new JsonObject()
                            .put("name", "Item 99")
                            .put("description", "Do something 100 times")
                            .put("due_date", "02/07/2022")
                            .put("priority", 99), testContext);

            testRequest(client.post(TEST_PORT, "localhost", addItemUrl(username)))
                    .expect(
                            statusCode(400),
                            bodyResponse(Buffer.buffer(
                                    "Item due date must be a valid YYYY-MM-DD format date"),
                                    null))
                    .sendJson(new JsonObject()
                            .put("name", "Item 99")
                            .put("description", "Do something 100 times")
                            .put("due_date", "2022-01-01")
                            .put("priority", 99), testContext);

        });
    }
}
