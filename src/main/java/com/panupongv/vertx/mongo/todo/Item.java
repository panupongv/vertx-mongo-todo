package com.panupongv.vertx.mongo.todo;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bson.types.ObjectId;

import io.vertx.core.json.JsonObject;

public class Item {
    public static final String MONGO_ID_KEY = "_id";
    public static final String NAME_KEY = "name";
    public static final String DESCRIPTION_KEY = "description";
    public static final String DUE_DATE_KEY = "due_date";
    public static final String PRIORITY_KEY = "priority";

    static final DateFormat mongoDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private ObjectId mongoId;
    private String name;
    private String description;
    private Date dueDate;
    private int priority;

    public Item(String name, String description, Date dueDate, int priority) {
        this(new ObjectId(), name, description, dueDate, priority);
    }

    public Item(ObjectId mongoId, String name, String description, Date dueDate, int priority) {
        this.mongoId = mongoId;
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
        this.priority = priority;
    }

    public JsonObject getMongoDbJson() {
        JsonObject itemJson = new JsonObject()
        .put(MONGO_ID_KEY, mongoId.toString())
        .put(NAME_KEY, name)
        .put(DESCRIPTION_KEY, description)
        .put(DUE_DATE_KEY, mongoDateFormat.format(dueDate))
        .put(PRIORITY_KEY, priority);

        return itemJson;
    }
}
