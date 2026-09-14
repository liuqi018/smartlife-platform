package com.smartlife.controller;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopControllerNearbyValidationTest {

    @Test
    void rejectsIllegalLongitude() throws Exception {
        assertFalse(validate(new Object[]{181D, 32.20573D, 5000D, null, 50}).isEmpty());
    }

    @Test
    void rejectsLimitAboveMaximum() throws Exception {
        assertFalse(validate(new Object[]{118.71111D, 32.20573D, 5000D, null, 101}).isEmpty());
    }

    @Test
    void acceptsNanjingDemoCenterAndDefaults() throws Exception {
        assertTrue(validate(new Object[]{118.71111D, 32.20573D, 5000D, null, 50}).isEmpty());
    }

    private Set<ConstraintViolation<ShopController>> validate(Object[] arguments) throws Exception {
        ShopController controller = new ShopController();
        ExecutableValidator validator = Validation.buildDefaultValidatorFactory().getValidator().forExecutables();
        Method method = ShopController.class.getMethod("queryNearbyShops",
                Double.class, Double.class, Double.class, Long.class, Integer.class);
        return validator.validateParameters(controller, method, arguments);
    }
}
