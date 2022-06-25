package panupongv.vertx.mongo.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import com.panupongv.vertx.mongo.todo.Item;

import io.vertx.core.json.JsonObject;

public class ItemTest {
    String sampleCharacter = "*";

    @Test
    public void testJsonValidation() {
        JsonObject emptyJson = new JsonObject();
        assertEquals(4, Item.collectErrorsFromJson(emptyJson).size());

        JsonObject missingFieldJson = new JsonObject()
                .put(Item.NAME_KEY, "Name")
                .put(Item.DESCRIPTION_KEY, "Description")
                .put(Item.PRIORITY_KEY, 123);
        assertEquals(1, Item.collectErrorsFromJson(missingFieldJson).size());

        JsonObject wrongTypeJson = new JsonObject()
                .put(Item.NAME_KEY, "Name")
                .put(Item.DESCRIPTION_KEY, 789)
                .put(Item.DUE_DATE_KEY, "2022-01-01")
                .put(Item.PRIORITY_KEY, 123);
        assertEquals(1, Item.collectErrorsFromJson(wrongTypeJson).size());

        JsonObject validJson = new JsonObject()
                .put(Item.NAME_KEY, "Name")
                .put(Item.DESCRIPTION_KEY, "Description")
                .put(Item.DUE_DATE_KEY, "2022-01-01")
                .put(Item.PRIORITY_KEY, 123);
        assumeTrue(Item.collectErrorsFromJson(validJson).isEmpty());
    }

    @Test
    public void testItemName() {
        assertFalse(Item.isValidName(""));
        assertFalse(Item.isValidName(StringUtils.repeat(sampleCharacter, 35)));
        assertTrue(Item.isValidName("About the right length"));
    }

    @Test
    public void testItemDescription() {
        assertFalse(Item.isValidDescription(StringUtils.repeat(sampleCharacter, 300)));
        assertTrue(Item.isValidDescription("About the right length"));
    }

    @Test
    public void testDateFormat() {
        assertFalse(Item.isValidDateFormat("01/02/2022"));
        assertFalse(Item.isValidDateFormat("01-02-2022"));
        assertFalse(Item.isValidDateFormat(""));
        assertTrue(Item.isValidDateFormat("2022-02-01"));
    }
}
