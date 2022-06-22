package com.panupongv.vertx.mongo.todo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerHandler;

public class WebVerticle extends AbstractVerticle {

    private static final int createUserTimeout = 7000;

    @Override
    public void start(Promise<Void> start) {
        configureRouter()
                .compose(this::startHttpServer)
                .onComplete(start::handle);
    }

    Future<Router> configureRouter() {
        Router router = Router.router(vertx);

        Handler<RoutingContext> checkUserExist = (RoutingContext ctx) -> checkUserExistence(ctx, true);
        Handler<RoutingContext> checkUserDoesNotExist = (RoutingContext ctx) -> checkUserExistence(ctx, false);

        router.route().handler(LoggerHandler.create());
        router.post("/api/v1/users/:username").handler(requestTimeout("Create User", createUserTimeout))
                .handler(checkUserDoesNotExist).handler(this::createUserHandler);

        return Future.succeededFuture(router);
    }

    Future<Void> startHttpServer(Router router) {
        HttpServer server = vertx.createHttpServer().requestHandler(router);
        return Future.<HttpServer>future(promise -> server.listen(8080, promise)).mapEmpty();
    }

    Handler<RoutingContext> requestTimeout(String requestName, int timeout) {
        if (timeout < 0)
            throw new IllegalArgumentException("Timeout duration must be a positive integer");
        return (RoutingContext ctx) -> {
            vertx.setTimer(timeout, d -> {
                try {
                    ctx.request().response().setStatusCode(500).end(
                            String.format("Request timeout: %s", requestName));
                } catch (IllegalStateException e) {
                    System.out.println("Request handled before timeout");
                }
            });
            ctx.next();
        };
    }

    void checkUserExistence(RoutingContext ctx, boolean userShouldExist) {
        String username = ctx.pathParam("username");
        vertx.eventBus().request(MongoVerticle.CHECK_USER_EXIST, username, databaseResult -> {
            if (databaseResult.succeeded()) {
                Boolean userExists = (Boolean) databaseResult.result().body();
                if (userExists == userShouldExist) {
                    ctx.next();
                } else {
                    ctx.request().response().setStatusCode(400).end(
                            userExists ? String.format("User '%s' already exist", username)
                                    : String.format("User '%s' does not exist", username));
                }
            } else {
                ctx.request().response().setStatusCode(500)
                        .end(String.format("Internal Server Error when looking up User '%s'", username));
            }
        });
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
                String message = reply.getString(MongoVerticle.REPLY_CONTENT_KEY);
                ctx.request().response().setStatusCode(statusCode).end(message);
            } else {
                System.out.println(databaseResult.cause());
                ctx.request().response().setStatusCode(500).end(databaseResult.cause().getMessage());
            }
        });
    }
}