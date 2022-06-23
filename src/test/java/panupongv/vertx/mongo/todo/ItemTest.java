package panupongv.vertx.mongo.todo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import com.panupongv.vertx.mongo.todo.Item;

public class ItemTest {
    String sampleCharacter = "*";

    @Test
    public void testItemName() {    
        assertFalse(Item.isValidName(""));
        assertFalse(Item.isValidName(StringUtils.repeat(sampleCharacter, 35)));
        assertTrue(Item.isValidName("About the right length"));
    }

    @Test
    public void testItemDescription() {
        assertFalse(Item.isValidDescription(""));
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
