package dev.requestportal.request.workflow;

import dev.requestportal.request.org.OrgDirectory;
import dev.requestportal.request.template.RequestTemplate;
import dev.requestportal.request.template.TemplateField;
import dev.requestportal.web.InvalidFieldsException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Checks submitted values against the template's field definitions; reports every problem at once. */
@Component
class FieldValueValidator {

    static final int MAX_TEXT_LENGTH = 500;
    static final int MAX_TEXTAREA_LENGTH = 5000;
    static final int MAX_FILES = 10;

    private final OrgDirectory orgDirectory;

    FieldValueValidator(OrgDirectory orgDirectory) {
        this.orgDirectory = orgDirectory;
    }

    List<SubmittedValue> validate(RequestTemplate template, Map<String, Object> input) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<String> knownKeys = template.getFields().stream().map(TemplateField::getKey).collect(Collectors.toSet());
        input.keySet().stream()
                .filter(key -> !knownKeys.contains(key))
                .forEach(key -> errors.put(key, "is not a field of this template"));

        List<SubmittedValue> values = new ArrayList<>();
        for (TemplateField field : template.getFields()) {
            List<String> value = normalize(input.get(field.getKey()));
            if (value == null) {
                errors.put(field.getKey(), "must be text, or a list of text for file fields");
            } else if (value.isEmpty()) {
                if (field.isRequired()) {
                    errors.put(field.getKey(), "is required");
                }
            } else {
                String error = check(field, value);
                if (error == null) {
                    values.add(new SubmittedValue(field, value));
                } else {
                    errors.put(field.getKey(), error);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        return values;
    }

    /** Blank text and empty lists count as "no value"; other JSON types (numbers, objects) are rejected. */
    private static List<String> normalize(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text.strip());
        }
        if (raw instanceof List<?> items) {
            List<String> texts = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof String text)) {
                    return null;
                }
                if (!text.isBlank()) {
                    texts.add(text.strip());
                }
            }
            return texts;
        }
        return null;
    }

    private String check(TemplateField field, List<String> value) {
        if (field.getType().holdsFiles()) {
            if (value.size() > MAX_FILES) {
                return "accepts at most " + MAX_FILES + " files";
            }
            return value.stream().allMatch(FieldValueValidator::isUuid) ? null : "must be a list of file ids";
        }
        if (value.size() != 1) {
            return "must be a single value";
        }
        String text = value.getFirst();
        return switch (field.getType()) {
            case TEXT -> text.length() > MAX_TEXT_LENGTH ? "must be at most " + MAX_TEXT_LENGTH + " characters" : null;
            case TEXTAREA -> text.length() > MAX_TEXTAREA_LENGTH
                    ? "must be at most " + MAX_TEXTAREA_LENGTH + " characters"
                    : null;
            case SELECT -> field.getOptions().contains(text)
                    ? null
                    : "must be one of: " + String.join(", ", field.getOptions());
            case TERM -> orgDirectory.termExists(text) ? null : "is not a known academic term";
            case SCHOOL_YEAR -> orgDirectory.schoolYearExists(text) ? null : "is not a known school year";
            case FILE, IMAGE -> throw new IllegalStateException("handled above");
        };
    }

    private static boolean isUuid(String text) {
        try {
            UUID.fromString(text);
            return text.length() == 36;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
