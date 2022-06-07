package com.panupongv.vertx.mongo.todo;

public class Utils {
    public static String convertJsonQuotes(String singleQuoteJson) {
        return singleQuoteJson.replaceAll("'", "\"");
    }
}
