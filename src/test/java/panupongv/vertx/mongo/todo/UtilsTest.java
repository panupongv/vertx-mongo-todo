package panupongv.vertx.mongo.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.panupongv.vertx.mongo.todo.Utils;

public class UtilsTest {
    @Test
    public void test() {
        String test = "''''";
        assertEquals("\"\"\"\"", Utils.convertJsonQuotes(test));
    }
}
