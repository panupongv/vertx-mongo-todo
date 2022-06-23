package com.panupongv.vertx.mongo.todo;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import io.vertx.core.json.JsonObject;

public class Item {
    public static final String MONGO_ID_KEY = "_id";
    public static final String NAME_KEY = "name";
    public static final String DESCRIPTION_KEY = "description";
    public static final String DUE_DATE_KEY = "due_date";
    public static final String PRIORITY_KEY = "priority";

    private ObjectId mongoId;
    private String name;
    private String description;
    private String dueDate;
    private int priority;

    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_DESCRIPTION_LENGTH = 256;
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}$");

    public Item(String name, String description, String dueDate, int priority) {
        this(new ObjectId(), name, description, dueDate, priority);
    }

    public Item(ObjectId mongoId, String name, String description, String dueDate, int priority) {

        if (!isValidName(name))
            throw new IllegalArgumentException("Item Constructor: Invalid Item Name");
        if (!isValidDescription(description))
            throw new IllegalArgumentException("Item Constructor: Invalid Item Description");
        if (!isValidDateFormat(dueDate))
            throw new IllegalArgumentException("Item Constructor: Invalid Date Format");

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
                .put(DUE_DATE_KEY, dueDate)
                .put(PRIORITY_KEY, priority);

        return itemJson;
    }

    public static boolean isValidName(String name) {
        return name.length() > 0 && name.length() <= MAX_NAME_LENGTH;
    }

    public static boolean isValidDescription(String description) {
        return description.length() > 0 && description.length() <= MAX_DESCRIPTION_LENGTH;
    }

    public static boolean isValidDateFormat(String date) {
        return DATE_PATTERN.matcher(date).matches();
    }
}
