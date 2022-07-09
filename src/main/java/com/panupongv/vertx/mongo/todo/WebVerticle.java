package com.panupongv.vertx.mongo.todo;

import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;

public class WebVerticle extends AbstractVerticle {

    private static final int CREATE_USER_TIMEOUT = 7000;
    private static final int ADD_ITEM_TIMEOUT = 7000;

    private enum UsernameSource {
        BODY,
        PATH
    }

    @Override
    public void start(Promise<Void> start) {
        configureRouter()
                .compose(this::startHttpServer)
                .onComplete(start::handle);
    }

    Future<Router> configureRouter() {
        Router router = Router.router(vertx);

        Handler<RoutingContext> checkUserDoesNotExistBeforeCreating = (RoutingContext ctx) -> checkUserExistence(ctx,
                false, UsernameSource.BODY);
        Handler<RoutingContext> checkUserExistBeforeOperations = (RoutingContext ctx) -> checkUserExistence(ctx, true,
                UsernameSource.PATH);

        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        router.post("/api/v1/users")
                .handler(requestTimeoutHandler("Create User", CREATE_USER_TIMEOUT))
                .handler(this::checkUsernameIsValid)
                .handler(checkUserDoesNotExistBeforeCreating)
                .handler(this::createUserHandler);

        router.post("/api/v1/users/:username/items")
                .handler(requestTimeoutHandler("Add Item", ADD_ITEM_TIMEOUT))
                .handler(checkUserExistBeforeOperations)
                .handler(this::addItemHandler);

        return Future.succeededFuture(router);
    }

    Future<Void> startHttpServer(Router router) {
        JsonObject http = config().getJsonObject("http", new JsonObject());
        int httpPort = http.getInteger("port", 7777);
        System.out.println("Running at port " + httpPort);
        HttpServer server = vertx.createHttpServer().requestHandler(router);
        return Future.<HttpServer>future(promise -> server.listen(httpPort, promise)).mapEmpty();
    }

    Handler<RoutingContext> requestTimeoutHandler(String requestName, int timeout) {
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

    void checkUsernameIsValid(RoutingContext ctx) {
        JsonObject jsonBody = ctx.getBodyAsJson();
        if (jsonBody == null) {
            ctx.request().response().setStatusCode(400).end("Missing JSON request body");
            return;
        }
        String username = ctx.getBodyAsJson().getString("username");
        if (username == null) {
            ctx.request().response().setStatusCode(400).end("A username must be provided");
            return;
        }
        if (!InputValidator.validateUsername(username)) {
            String message = String.format(
                    "Username must contain only alphabets and numbers, and must be between %d and %d characters long",
                    InputValidator.USERNAME_MIN_LENGTH,
                    InputValidator.USERNAME_MAX_LENGTH);
            ctx.request().response().setStatusCode(400).end(message);
            return;
        }
        ctx.next();
    }

    void checkUserExistence(RoutingContext ctx, boolean userShouldExist, UsernameSource usernameSource) {
        String tempUsername = null;
        if (usernameSource == UsernameSource.BODY) {
            tempUsername = ctx.getBodyAsJson().getString("username");
        } else if (usernameSource == UsernameSource.PATH) {
            tempUsername = ctx.pathParam("username");
        }

        final String username = tempUsername;
        vertx.eventBus().request(MongoVerticle.CHECK_USER_EXIST, username, databaseResult -> {
            if (databaseResult.succeeded()) {
                Boolean userExists = (Boolean) databaseResult.result().body();
                if (userExists == userShouldExist) {
                    ctx.next();
                } else {
                    ctx.request().response().setStatusCode(400).end(
                            userExists ? String.format("User '%s' already exists", username)
                                    : String.format("User '%s' does not exist", username));
                }
            } else {
                ctx.request().response().setStatusCode(500)
                        .end(String.format("Internal Server Error when looking up User '%s'", username));
            }
        });
    }

    void createUserHandler(RoutingContext ctx) {
        String username = ctx.getBodyAsJson().getString("username");
        vertx.eventBus().request(MongoVerticle.CREATE_USER, username, standardReplyHandler(ctx));
    }

    void addItemHandler(RoutingContext ctx) {
        String username = ctx.pathParam("username");
        JsonObject itemJson = ctx.getBodyAsJson();
        if (itemJson == null) {
            ctx.request().response().setStatusCode(400).end("Missing request body");
            return;
        }

        List<String> errorsFromJson = Item.collectErrorsFromJson(itemJson);
        if (errorsFromJson.isEmpty()) {
            vertx.eventBus().request(
                    MongoVerticle.ADD_ITEM,
                    MongoVerticle.addItemMessage(username, itemJson),
                    standardReplyHandler(ctx));
        } else {
            String errorResponse = String.join("\n", errorsFromJson);
            ctx.request().response().setStatusCode(400).end(errorResponse);
        }

    }

    private Handler<AsyncResult<Message<Object>>> standardReplyHandler(RoutingContext ctx) {
        return (AsyncResult<Message<Object>> asyncResult) -> {
            if (asyncResult.succeeded()) {
                JsonObject resultJson = (JsonObject) asyncResult.result().body();
                ctx.request().response()
                        .setStatusCode(resultJson.getInteger(MongoVerticle.REPLY_STATUS_CODE_KEY))
                        .end(resultJson.getString(MongoVerticle.REPLY_CONTENT_KEY));
            } else {
                System.out.println(asyncResult.cause());
                ctx.request().response().setStatusCode(500).end(asyncResult.cause().getMessage());
            }
        };
    }
}