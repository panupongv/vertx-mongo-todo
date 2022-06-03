package com.panupongv.vertx.mongo.todo;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.vertx.core.json.JsonObject;

public class Item {
    public static final String COLLECTION_NAME = "items";
    static final DateFormat mongoDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private String mongoId;
    private String name;
    private String description;
    private Date dueDate;
    private int priority;

    public Item(String name, String description, Date dueDate, int priority) {
        this(null, name, description, dueDate, priority);
    }

    public Item(String mongoId, String name, String description, Date dueDate, int priority) {
        this.mongoId = mongoId;
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
        this.priority = priority;
    }

    public JsonObject getMongoDbJson() {
        JsonObject itemJson = new JsonObject()
        //.put("id", mongoId == null? "":mongoId) "2022-06-05T00:00:00+00:00"
        .put("name", name)
        .put("description", description)
        .put("due_date", mongoDateFormat.format(dueDate))
        .put("priority", priority);

        return itemJson;
    }
}
