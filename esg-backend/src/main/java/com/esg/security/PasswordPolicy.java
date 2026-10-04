package com.esg.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Password rules (WP-12, TRD §9): at least 12 characters with upper case, lower case, a digit and a symbol,
 * and not containing the email's local part. The UI mirrors these rules (passwordSchema in the frontend).
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /** Returns the list of violated rules; empty means the password is acceptable. */
    public List<String> violations(String password, String email) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("at least " + MIN_LENGTH + " characters");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            problems.add("at most " + MAX_LENGTH + " characters");
        }
        if (password == null) {
            return problems;
        }
        if (!password.matches(".*[A-Z].*")) problems.add("an upper-case letter");
        if (!password.matches(".*[a-z].*")) problems.add("a lower-case letter");
        if (!password.matches(".*\\d.*")) problems.add("a digit");
        if (!password.matches(".*[^A-Za-z0-9].*")) problems.add("a symbol");
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (local.length() >= 3 && password.toLowerCase(Locale.ROOT).contains(local)) {
                problems.add("must not contain your email name");
            }
        }
        return problems;
    }
}
