package com.panupongv.vertx.mongo.todo;

import io.vertx.core.AbstractVerticle;

public class WebVerticle extends AbstractVerticle {

    @Override
    public void start() {
        System.out.println("Web Verticle Deployed");
    }
}