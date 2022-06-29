package com.panupongv.vertx.mongo.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class InputValidatorTest {
    @Test
    public void inputValidatorShouldNotAllowShortUsername() {
        // Given a username shorter than the lower limit
        String shortUsername = "XYZ";

        // When validated
        boolean validationResult = InputValidator.validateUsername(shortUsername);

        // Then the result should be false
        assertEquals(false, validationResult);
    }
}
