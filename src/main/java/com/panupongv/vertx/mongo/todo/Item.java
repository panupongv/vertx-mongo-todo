package com.panupongv.vertx.mongo.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public static List<String> collectErrorsFromJson(JsonObject itemJson) {
        Map<String, Object> itemMap = itemJson.getMap();
        List<String> errors = new ArrayList<String>();

        if (itemMap.size() > 4) {
            errors.add(String.format("Only 4 fields allowed: %s, %s, %s and %s",
                    NAME_KEY,
                    DESCRIPTION_KEY,
                    DUE_DATE_KEY,
                    PRIORITY_KEY));
        }

        Object name = itemMap.get(NAME_KEY);
        if (name == null)
            errors.add(String.format("Missing item name, please use the key '%s'", NAME_KEY));
        else if (!(name instanceof String))
            errors.add("Item name must be a string");
        else if (!isValidName((String) name))
            errors.add(String.format("Item name must be a non-empty string with %d characters limit", MAX_NAME_LENGTH));

        Object description = itemMap.get(DESCRIPTION_KEY);
        if (description == null)
            errors.add(String.format("Missing item description, please use the key '%s'", DESCRIPTION_KEY));
        else if (!(description instanceof String))
            errors.add("Item description must be a string");
        else if (!isValidDescription((String) description))
            errors.add(String.format("Item description must be not exceed %d characters", MAX_DESCRIPTION_LENGTH));

        Object dueDate = itemMap.get(DUE_DATE_KEY);
        if (dueDate == null)
            errors.add(String.format("Missing the due date, please use the key '%s'", DUE_DATE_KEY));
        else if (!(dueDate instanceof String))
            errors.add("Item due date must be a string");
        else if (!isValidDateFormat((String) dueDate))
            errors.add("Item due date must be in the YYYY-MM-DD format");

        Object priority = itemMap.get(PRIORITY_KEY);
        if (priority == null)
            errors.add(String.format("Missing item priority, please use the key '%s'", PRIORITY_KEY));
        else if (!(priority instanceof Integer))
            errors.add("Item priority must be an integer");

        return errors;
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
        return description.length() <= MAX_DESCRIPTION_LENGTH;
    }

    public static boolean isValidDateFormat(String date) {
        return DATE_PATTERN.matcher(date).matches();
    }
}
