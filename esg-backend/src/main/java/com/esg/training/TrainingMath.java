package com.esg.training;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

/** Training arithmetic (WP-42, D37 (4)–(7)), free of the database for golden tests (TrainingMathTest). */
public final class TrainingMath {
    private TrainingMath() {}

    /** Letters and digits that can't be confused when read aloud or typed (no I, O, 0, 1). */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Correct answers ÷ questions × 100, rounded half-up; an unanswered question (null or -1) is wrong. */
    public static int score(List<Integer> answers, List<Integer> correct) {
        if (correct.isEmpty()) {
            return 100;
        }
        int right = 0;
        for (int i = 0; i < correct.size(); i++) {
            Integer a = i < answers.size() ? answers.get(i) : null;
            if (a != null && a.equals(correct.get(i))) {
                right++;
            }
        }
        return BigDecimal.valueOf(100L * right).divide(BigDecimal.valueOf(correct.size()), 0, RoundingMode.HALF_UP).intValue();
    }

    public static boolean passed(int score, int passingScore) {
        return score >= passingScore;
    }

    /** Training hours per employee to one decimal; null without a headcount. */
    public static BigDecimal hoursPerEmployee(BigDecimal hours, long headcount) {
        return headcount <= 0 ? null : hours.divide(BigDecimal.valueOf(headcount), 1, RoundingMode.HALF_UP);
    }

    /** Share of assigned enrolments passed, one decimal; null when nothing was assigned. */
    public static BigDecimal completionRate(long passed, long assigned) {
        return assigned == 0 ? null : BigDecimal.valueOf(100.0 * passed / assigned).setScale(1, RoundingMode.HALF_UP);
    }

    public static LocalDate expiry(LocalDate issued, Integer validityMonths) {
        return validityMonths == null ? null : issued.plusMonths(validityMonths);
    }

    /** valid / expired / revoked on the given day. */
    public static String status(LocalDate expiresOn, boolean revoked, LocalDate today) {
        return revoked ? "revoked" : expiresOn != null && !today.isBefore(expiresOn) ? "expired" : "valid";
    }

    static String newCode() {
        StringBuilder b = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return b.toString();
    }
}
