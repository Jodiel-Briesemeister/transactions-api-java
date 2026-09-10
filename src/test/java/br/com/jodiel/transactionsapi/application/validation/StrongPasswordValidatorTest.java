package br.com.jodiel.transactionsapi.application.validation;

import br.com.jodiel.transactionsapi.application.dtos.auth.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<ConstraintViolation<RegisterRequest>> validate(String password) {
        return validator.validateProperty(
                new RegisterRequest("John Doe", "john@example.com", password, null), "password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Str0ng!Pass", "M1nha$enhaF0rte", "Zx9#kLmQ"})
    @DisplayName("accepts passwords that meet every rule")
    void acceptsStrongPasswords(String password) {
        assertThat(validate(password)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "Ab1!c,            Password must be at least 8 characters",
            "str0ng!pass,      Password must contain at least one uppercase letter",
            "STR0NG!PASS,      Password must contain at least one lowercase letter",
            "Strong!Pass,      Password must contain at least one number",
            "Str0ngPassword,   Password must contain at least one special character",
            "Str0ng!aaaaPass,  Password must not contain 4 or more repeated characters",
            "Str0ng!abcPass,   Password must not contain sequential characters"
    })
    @DisplayName("rejects each policy violation with a specific message")
    void rejectsWeakPasswords(String password, String expectedMessage) {
        assertThat(validate(password))
                .singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("leaves the null case to @NotBlank so the user gets one message, not two")
    void ignoresNull() {
        // Asserts which constraint fired rather than its text: the @NotBlank message comes from
        // Hibernate Validator's resource bundle and is translated to the JVM's locale.
        assertThat(validate(null))
                .singleElement()
                .extracting(v -> v.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(NotBlank.class);
    }
}
