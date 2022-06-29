package com.panupongv.vertx.mongo.todo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.reactiverse.junit5.web.WebClientOptionsInject;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import static io.reactiverse.junit5.web.TestRequest.*;

@ExtendWith({
        VertxExtension.class,
})
public class MainVerticleTest {

    Vertx vertx = Vertx.vertx();
    // VertxTestContext testContext = new VertxTestContext();
    String deploymentId;

    @WebClientOptionsInject
    // public WebClientOptions opts = new WebClientOptions()
    // .setDefaultPort(9000)
    // .setDefaultHost("localhost");

    // WebClient client = WebClient.create(vertx, opts);
    WebClient client = WebClient.create(vertx);

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        System.out.println("Set up");
        
        DeploymentOptions options = new DeploymentOptions()
                .setConfig(new JsonObject().put("http.port", 8081));

        testContext
                .assertComplete(vertx.deployVerticle(MainVerticle.class.getName(), options))
                .onComplete(ar -> {
                    deploymentId = ar.result();
                    testContext.completeNow();
                });
    }
    // end::setUp[]

    // tag::tearDown[]
    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        System.out.println("Tear down");
        testContext
                .assertComplete(vertx.undeploy(deploymentId))
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    public void testCreateUser(Vertx vertx, VertxTestContext testContext) {
        testRequest(client.post(8080, "localhost", "/api/v1/users"))
                .expect(statusCode(200))
                .sendJson(new JsonObject().put("username", "newuser"), testContext)
                .onComplete(x -> {
                    System.out.println("posted");
                    System.out.println(x.result().body());
                    System.out.println(x.succeeded());
                });

        System.out.println("ran");
    }
}
