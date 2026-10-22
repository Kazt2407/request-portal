package dev.requestportal.request.template;

import dev.requestportal.request.org.OrgDirectory;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import dev.requestportal.web.ConflictException;
import dev.requestportal.web.InvalidFieldsException;
import dev.requestportal.web.NotFoundException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private final RequestTemplateRepository templates;
    private final OrgDirectory orgDirectory;
    private final Clock clock;

    public TemplateService(RequestTemplateRepository templates, OrgDirectory orgDirectory, Clock clock) {
        this.templates = templates;
        this.orgDirectory = orgDirectory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TemplateSummary> list(TemplateStatus status) {
        List<RequestTemplate> found = status == null
                ? templates.findAllByOrderByTitleAsc()
                : templates.findByStatusOrderByTitleAsc(status);
        return found.stream().map(TemplateSummary::from).toList();
    }

    /** Active templates the user may submit requests from. */
    @Transactional(readOnly = true)
    public List<TemplateSummary> available(CurrentUser user) {
        Set<Audience> audiences = Audience.admitting(user);
        if (audiences.isEmpty()) {
            return List.of();
        }
        return templates.findByStatusAndAudienceInOrderByTitleAsc(TemplateStatus.ACTIVE, audiences).stream()
                .map(TemplateSummary::from)
                .toList();
    }

    /** Admins see every template; requesters only the active ones offered to them. */
    @Transactional(readOnly = true)
    public TemplateResponse get(Long id, CurrentUser user) {
        RequestTemplate template = templates.findById(id)
                .filter(found -> user.hasRole(Role.ADMIN)
                        || (found.getStatus() == TemplateStatus.ACTIVE && found.getAudience().admits(user)))
                .orElseThrow(() -> new NotFoundException("Template " + id + " not found"));
        return TemplateResponse.from(template);
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        validate(request);
        RequestTemplate template = templates.save(new RequestTemplate(request, clock.instant()));
        return TemplateResponse.from(template);
    }

    @Transactional
    public TemplateResponse update(Long id, TemplateRequest request) {
        RequestTemplate template = templates.findById(id)
                .orElseThrow(() -> new NotFoundException("Template " + id + " not found"));
        if (request.version() == null) {
            throw new InvalidFieldsException(Map.of("version", "is required when updating"));
        }
        if (!Objects.equals(template.getVersion(), request.version())) {
            throw new ConflictException("Template " + id + " was changed by someone else; reload it and try again");
        }
        validate(request);
        template.apply(request, clock.instant());
        templates.flush();
        return TemplateResponse.from(template);
    }

    /** Rules bean validation cannot express, reported per field path like {@code fields[2].options}. */
    private void validate(TemplateRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        Map<String, Integer> firstIndexOfKey = new HashMap<>();
        for (int i = 0; i < request.fields().size(); i++) {
            TemplateRequest.FieldDefinition field = request.fields().get(i);
            Integer previous = firstIndexOfKey.putIfAbsent(field.key(), i);
            if (previous != null) {
                errors.put("fields[" + i + "].key", "duplicates fields[" + previous + "].key");
            }
            List<String> options = field.optionsOrEmpty().stream().map(String::strip).toList();
            if (field.type() == FieldType.SELECT && options.isEmpty()) {
                errors.put("fields[" + i + "].options", "must list at least one option");
            } else if (new HashSet<>(options).size() != options.size()) {
                errors.put("fields[" + i + "].options", "must not contain duplicates");
            }
        }

        Map<String, String> departmentPaths = new LinkedHashMap<>();
        departmentPaths.put("departmentCode", request.departmentCode());
        for (int i = 0; i < request.steps().size(); i++) {
            departmentPaths.put("steps[" + i + "].departmentCode", request.steps().get(i).departmentCode());
        }
        Set<String> unknown = orgDirectory.unknownDepartments(Set.copyOf(departmentPaths.values()));
        departmentPaths.forEach((path, code) -> {
            if (unknown.contains(code)) {
                errors.put(path, "is not a known department");
            }
        });

        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
    }
}
