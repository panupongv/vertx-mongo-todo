package com.panupongv.vertx.mongo.todo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;


public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> start) {
        deployVerticles().onComplete(start::handle);
    }

    Future<Void> deployVerticles() {
        Future<Void> webVerticle = Future.future(promise -> vertx.deployVerticle(new WebVerticle()));
        Future<Void> mongoVerticle = Future.future(promise -> vertx.deployVerticle(new MongoVerticle()));
        
        return CompositeFuture.all(webVerticle, mongoVerticle).mapEmpty();
    }

}
