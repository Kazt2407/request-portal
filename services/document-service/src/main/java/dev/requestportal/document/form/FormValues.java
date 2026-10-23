package dev.requestportal.document.form;

import dev.requestportal.security.CurrentUser;
import dev.requestportal.web.InvalidFieldsException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The values a form can use, and the checks on what users type. */
final class FormValues {

    /** From the access token. */
    static final Set<String> IDENTITY_KEYS = Set.of("full_name", "email", "username");
    /** From the student record in org-service. */
    static final Set<String> PROFILE_KEYS =
            Set.of("student_code", "date_of_birth", "class_name", "faculty", "major", "phone");
    /** Today, for the "ngày ... tháng ... năm ..." line most Vietnamese forms end with. */
    static final Set<String> DATE_KEYS = Set.of("today", "day", "month", "year");
    /** Printed where a value is unknown, so the form can still be completed by hand. */
    static final String BLANK = "……………………";
    static final int MAX_TEXT_LENGTH = 500;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FormValues() {
    }

    static boolean isBuiltIn(String key) {
        return IDENTITY_KEYS.contains(key) || PROFILE_KEYS.contains(key) || DATE_KEYS.contains(key);
    }

    /** Built-in keys, the form's own fields, and {@code <key>_words} for each NUMBER field. */
    static boolean isAllowed(String placeholder, FormViews.FormRequest form) {
        return isBuiltIn(placeholder) || form.fields().stream().anyMatch(field -> field.key().equals(placeholder)
                || (field.type() == FormFieldType.NUMBER && placeholder.equals(field.key() + "_words")));
    }

    static Map<String, String> resolve(FormTemplate form, Map<String, String> typed, CurrentUser user,
            Optional<ProfileDirectory.Profile> profile, LocalDate today) {
        Map<String, String> values = new HashMap<>();
        values.put("full_name", user.fullName());
        values.put("email", user.email());
        values.put("username", user.username());
        values.put("today", DATE.format(today));
        values.put("day", "%02d".formatted(today.getDayOfMonth()));
        values.put("month", "%02d".formatted(today.getMonthValue()));
        values.put("year", String.valueOf(today.getYear()));
        profile.ifPresent(p -> {
            values.put("student_code", p.studentCode());
            values.put("date_of_birth", p.dateOfBirth() == null ? null : DATE.format(p.dateOfBirth()));
            values.put("class_name", p.className());
            values.put("faculty", p.faculty());
            values.put("major", p.major());
            values.put("phone", p.phone());
        });
        values.putAll(typedValues(form, typed));
        Map<String, String> filled = new HashMap<>();
        for (String placeholder : form.getPlaceholders()) {
            String value = values.get(placeholder);
            filled.put(placeholder, value == null || value.isBlank() ? BLANK : value);
        }
        return filled;
    }

    private static Map<String, String> typedValues(FormTemplate form, Map<String, String> typed) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> values = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (FormField field : form.getFields()) {
            keys.add(field.getKey());
            String raw = typed.get(field.getKey());
            if (raw == null || raw.isBlank()) {
                if (field.isRequired()) {
                    errors.put(field.getKey(), "is required");
                }
                continue;
            }
            String text = raw.strip();
            switch (field.getType()) {
                case TEXT -> {
                    if (text.length() > MAX_TEXT_LENGTH) {
                        errors.put(field.getKey(), "must be at most " + MAX_TEXT_LENGTH + " characters");
                    } else {
                        values.put(field.getKey(), text);
                    }
                }
                case NUMBER -> {
                    String digits = text.replaceAll("[\\s.,]", "");
                    if (!digits.matches("\\d{1,15}")) {
                        errors.put(field.getKey(), "must be a whole number up to " + VietnameseNumberWords.MAX);
                    } else {
                        long number = new BigInteger(digits).longValueExact();
                        values.put(field.getKey(), "%,d".formatted(number).replace(',', '.'));
                        values.put(field.getKey() + "_words", VietnameseNumberWords.toWords(number));
                    }
                }
                case DATE -> {
                    try {
                        values.put(field.getKey(), DATE.format(LocalDate.parse(text)));
                    } catch (DateTimeParseException exception) {
                        errors.put(field.getKey(), "must be a date like 2026-10-22");
                    }
                }
            }
        }
        typed.keySet().stream()
                .filter(key -> !keys.contains(key))
                .forEach(key -> errors.put(key, "is not a field of this form"));
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        return values;
    }
}
