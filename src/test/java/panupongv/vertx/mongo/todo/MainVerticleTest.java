package panupongv.vertx.mongo.todo;

import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

public class MainVerticleTest {

    @Test
    public void doSomethingWithVerticle() {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);
        String username = "asd";

    }
}
