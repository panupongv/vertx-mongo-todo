package com.panupongv.vertx.mongo.todo;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {

    final String RUN_MODE_TEST = "test";
    final String RUN_MODE_APP = "app";
    final DeploymentOptions deploymentOptions = new DeploymentOptions();

    @Override
    public void start(Promise<Void> start) {
        loadConfig()
                .compose(this::deployMongoVerticle)
                .compose(this::deployWebVerticle)
                .onComplete(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        System.out.println("Deployment Successful");
                        start.complete();
                    } else {
                        String cause = asyncResult.cause().getMessage();
                        System.out.println("Deployment Failed");
                        System.out.println(cause);
                        start.fail(cause);
                    }
                });
    }

    Future<Void> loadConfig() {
        String runMode = config().getString("runMode", RUN_MODE_TEST);
        System.out.println(String.format("Running in %s mode", runMode));

        if (runMode.equals(RUN_MODE_APP)) {
            deploymentOptions.setConfig(config());
            return Future.succeededFuture();
        }

        ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "testConfig.json"));

        ConfigRetrieverOptions opts = new ConfigRetrieverOptions()
                .addStore(defaultConfig);

        ConfigRetriever retriever = ConfigRetriever.create(vertx, opts);

        return retriever.getConfig().onComplete(retrievedConfig -> {
            if (retrievedConfig.succeeded()) {
                deploymentOptions.setConfig(retrievedConfig.result());
            }
        }).mapEmpty();
    }

    Future<Void> deployMongoVerticle(Void unused) {
        return vertx.deployVerticle(new MongoVerticle(), deploymentOptions).mapEmpty();
    }

    Future<Void> deployWebVerticle(Void unused) {
        return vertx.deployVerticle(new WebVerticle(), deploymentOptions).mapEmpty();
    }
}
