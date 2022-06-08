package com.panupongv.vertx.mongo.todo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class WebVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> start) {
        configureRouter()
                .compose(this::startHttpServer)
                .onComplete(start::handle);
    }

    Future<Router> configureRouter() {
        Router router = Router.router(vertx);

        router.post("/api/v1/users/:username").handler(this::createUserHandler);

        return Future.succeededFuture(router);
    }

    Future<Void> startHttpServer(Router router) {
        HttpServer server = vertx.createHttpServer().requestHandler(router);
        return Future.<HttpServer>future(promise -> server.listen(8080, promise)).mapEmpty();
    }

    void createUserHandler(RoutingContext ctx) {
        String username = ctx.pathParam("username");
        if (!InputValidator.validateUsername(username)) {
            String message = String.format(
                    "Username must contain only alphabets and numbers, and must be between %d and %d characters long",
                    InputValidator.USERNAME_MIN_LENGTH,
                    InputValidator.USERNAME_MAX_LENGTH);
            ctx.request().response().setStatusCode(400).end(message);
            return;
        }
        vertx.eventBus().request(MongoVerticle.CREATE_USER, username, databaseResult -> {
            if (databaseResult.succeeded()) {
                JsonObject reply = (JsonObject) databaseResult.result().body();
                int statusCode = reply.getInteger(MongoVerticle.REPLY_STATUS_CODE_KEY);
                String message = reply.getString(MongoVerticle.REPLY_MESSAGE_CODE_KEY);
                ctx.request().response().setStatusCode(statusCode).end(message);
            } else {
                System.out.println(databaseResult.cause());
                ctx.request().response().setStatusCode(500).end(databaseResult.cause().getMessage());
            }
        });
    }
}