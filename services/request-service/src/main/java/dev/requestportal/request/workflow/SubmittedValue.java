package dev.requestportal.request.workflow;

import dev.requestportal.request.template.TemplateField;
import java.util.List;

/** A validated value for one template field: one element, or several file ids. */
public record SubmittedValue(TemplateField field, List<String> value) {
}
