package com.panupongv.vertx.mongo.todo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> start) {
        deployMongoVerticle()
                .compose(this::deployWebVerticle)
                .onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        System.out.println("Deployment Successful");
                        start.handle(asyncResult);
                    } else {
                        String cause = asyncResult.cause().getMessage();
                        System.out.println("Deployment Failed");
                        System.out.println(cause);
                        start.fail(cause);
                    }
                });
    }

    Future<Void> deployMongoVerticle() {
        return vertx.deployVerticle(new MongoVerticle()).mapEmpty();
    }

    Future<Void> deployWebVerticle(Void unused) {
        return vertx.deployVerticle(new WebVerticle()).mapEmpty();
    }
}
