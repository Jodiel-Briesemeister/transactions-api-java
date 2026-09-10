package br.com.jodiel.transactionsapi.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");
    /** Four or more of the same character in a row, e.g. "aaaa". */
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{3,}");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Null is left to @NotBlank so the user gets one clear message per problem.
        if (password == null) return true;

        String violation = firstViolation(password);
        if (violation == null) return true;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(violation).addConstraintViolation();
        return false;
    }

    private String firstViolation(String password) {
        if (password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        if (!UPPERCASE.matcher(password).find()) {
            return "Password must contain at least one uppercase letter";
        }
        if (!LOWERCASE.matcher(password).find()) {
            return "Password must contain at least one lowercase letter";
        }
        if (!DIGIT.matcher(password).find()) {
            return "Password must contain at least one number";
        }
        if (!SYMBOL.matcher(password).find()) {
            return "Password must contain at least one special character";
        }
        if (REPEATED.matcher(password).find()) {
            return "Password must not contain 4 or more repeated characters";
        }
        if (hasSequentialRun(password)) {
            return "Password must not contain sequential characters";
        }
        return null;
    }

    /** Rejects three consecutive code points such as "abc" or "789". */
    private boolean hasSequentialRun(String password) {
        for (int i = 0; i + 2 < password.length(); i++) {
            if (password.charAt(i + 1) == password.charAt(i) + 1
                    && password.charAt(i + 2) == password.charAt(i) + 2) {
                return true;
            }
        }
        return false;
    }
}
