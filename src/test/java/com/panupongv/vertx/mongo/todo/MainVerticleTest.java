package com.panupongv.vertx.mongo.todo;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.reactiverse.junit5.web.WebClientOptionsInject;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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

    @BeforeAll
    public static void initialSetup(Vertx vertx, VertxTestContext testContext) {
        System.out.println("Initial set up");

        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "todo-app-test");
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
        testRequest(client.post(TEST_PORT, "localhost", "/api/v1/users"))
                .expect(statusCode(200))
                .sendJson(new JsonObject().put("username", "newuser"), testContext)
                .onComplete(x -> {
                    System.out.println(x.result().statusCode());
                    System.out.println(x.result().body());
                    System.out.println(x.succeeded());
                });
    }

    @Test
    public void testCreateInvalidUsernames(Vertx vertx, VertxTestContext testContext) {
        testRequest(client.post(TEST_PORT, "localhost", "/api/v1/users"))
                .expect(statusCode(400))
                .send(testContext)
                .onComplete(x -> {
                    System.out.println(x.result().statusCode());
                    System.out.println(x.result().body());
                    System.out.println(x.succeeded());
                });

        testRequest(client.post(TEST_PORT, "localhost", "/api/v1/users"))
                .expect(statusCode(400))
                .sendJson(new JsonObject().put("random_key", "random value"), testContext)
                .onComplete(x -> {
                    System.out.println(x.result().statusCode());
                    System.out.println(x.result().body());
                    System.out.println(x.succeeded());
                });

        testRequest(client.post(TEST_PORT, "localhost", "/api/v1/users"))
                .expect(statusCode(400))
                .sendJson(new JsonObject().put("username", ""), testContext)
                .onComplete(x -> {
                    System.out.println(x.result().statusCode());
                    System.out.println(x.result().body());
                    System.out.println(x.succeeded());
                });

        testRequest(client.post(TEST_PORT, "localhost", "/api/v1/users"))
                .expect(statusCode(400))
                .sendJson(new JsonObject().put("username", StringUtils.repeat("x", 40)), testContext)
                .onComplete(x -> {
                    System.out.println(x.result().statusCode());
                    System.out.println(x.result().body());
                    System.out.println(x.succeeded());
                });
    }

    @Test
    public void testCreateExistingUser(Vertx vertx, VertxTestContext testContext) {
        String username = "newuser";
        mongoTestUtil.insertUser(username).onComplete(x -> {
            testRequest(client.post(TEST_PORT, "localhost", "/api/v1/users"))
                    .expect(statusCode(400))
                    .sendJson(new JsonObject().put("username", username), testContext)
                    .onComplete(response -> {
                        System.out.println("posted");
                        System.out.println(response.result().statusCode());
                        System.out.println(response.result().body());
                        System.out.println(response.succeeded());
                    });
        });

    }
}
