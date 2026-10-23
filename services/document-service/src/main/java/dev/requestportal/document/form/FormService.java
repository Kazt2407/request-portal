package dev.requestportal.document.form;

import dev.requestportal.document.file.FileService;
import dev.requestportal.document.file.StoredFile;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import dev.requestportal.web.ConflictException;
import dev.requestportal.web.InvalidFieldsException;
import dev.requestportal.web.NotFoundException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fillable .docx forms: admins define them, students and lecturers download them filled in. */
@Service
public class FormService {

    private final FormTemplateRepository forms;
    private final FileService files;
    private final ProfileDirectory profiles;
    private final Clock clock;
    private final ZoneId zone;

    FormService(FormTemplateRepository forms, FileService files, ProfileDirectory profiles, Clock clock,
            @Value("${portal.time-zone}") String zone) {
        this.forms = forms;
        this.files = files;
        this.profiles = profiles;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @Transactional
    public FormViews.Form create(FormViews.FormRequest request) {
        List<String> placeholders = validate(request);
        FormTemplate form = forms.save(new FormTemplate(request, placeholders, clock.instant()));
        files.keep(request.templateFileId());
        return FormViews.Form.of(form);
    }

    @Transactional
    public FormViews.Form update(Long id, FormViews.FormRequest request) {
        FormTemplate form = find(id);
        if (request.version() == null) {
            throw new InvalidFieldsException(Map.of("version", "is required when updating"));
        }
        if (!Objects.equals(form.getVersion(), request.version())) {
            throw new ConflictException("Form " + id + " was changed by someone else; reload it and try again");
        }
        List<String> placeholders = validate(request);
        form.apply(request, placeholders, clock.instant());
        files.keep(request.templateFileId());
        forms.flush();
        return FormViews.Form.of(form);
    }

    @Transactional(readOnly = true)
    public List<FormViews.Form> list() {
        return forms.findAllByOrderByTitleAsc().stream().map(FormViews.Form::of).toList();
    }

    @Transactional(readOnly = true)
    public List<FormViews.Form> available(CurrentUser user) {
        Set<FormAudience> audiences = EnumSet.noneOf(FormAudience.class);
        for (FormAudience audience : FormAudience.values()) {
            if (audience.admits(user)) {
                audiences.add(audience);
            }
        }
        if (audiences.isEmpty()) {
            return List.of();
        }
        return forms.findByActiveTrueAndAudienceInOrderByTitleAsc(audiences).stream().map(FormViews.Form::of).toList();
    }

    @Transactional(readOnly = true)
    public FormViews.Form get(Long id, CurrentUser user) {
        return FormViews.Form.of(findVisible(id, user));
    }

    /** The form filled with the user's identity and profile, today's date and the values they typed. */
    @Transactional(readOnly = true)
    public FormViews.FilledDocument fill(Long id, Map<String, String> typed, CurrentUser user) {
        FormTemplate form = findVisible(id, user);
        boolean needsProfile = !Collections.disjoint(form.getPlaceholders(), FormValues.PROFILE_KEYS);
        Optional<ProfileDirectory.Profile> profile = needsProfile ? profiles.mine() : Optional.empty();
        Map<String, String> values =
                FormValues.resolve(form, typed, user, profile, LocalDate.now(clock.withZone(zone)));
        StoredFile template = files.formTemplate(form.getTemplateFileId());
        byte[] document = DocxTemplate.fill(files.read(template), values);
        return new FormViews.FilledDocument(form.getTitle() + ".docx", document);
    }

    /** Checks the fields and the template; returns the template's placeholders. */
    private List<String> validate(FormViews.FormRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < request.fields().size(); i++) {
            String key = request.fields().get(i).key();
            Integer previous = seen.putIfAbsent(key, i);
            if (previous != null) {
                errors.put("fields[" + i + "].key", "duplicates fields[" + previous + "].key");
            } else if (FormValues.isBuiltIn(key) || key.endsWith("_words")) {
                errors.put("fields[" + i + "].key", "is reserved for values the form fills in itself");
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }

        StoredFile template = files.formTemplate(request.templateFileId());
        Set<String> placeholders;
        try {
            placeholders = DocxTemplate.placeholders(files.read(template));
        } catch (UncheckedIOException exception) {
            throw new InvalidFieldsException(Map.of("templateFileId", "is not a valid .docx document"));
        }
        List<String> unknown = new ArrayList<>();
        placeholders.stream().filter(name -> !FormValues.isAllowed(name, request)).forEach(unknown::add);
        if (!unknown.isEmpty()) {
            throw new InvalidFieldsException(Map.of("templateFileId",
                    "uses placeholders the form cannot fill: " + String.join(", ", unknown)));
        }
        return List.copyOf(placeholders);
    }

    private FormTemplate find(Long id) {
        return forms.findById(id).orElseThrow(() -> new NotFoundException("Form " + id + " not found"));
    }

    /** Admins see every form; others only active forms meant for them. */
    private FormTemplate findVisible(Long id, CurrentUser user) {
        return forms.findById(id)
                .filter(form -> user.hasRole(Role.ADMIN) || (form.isActive() && form.getAudience().admits(user)))
                .orElseThrow(() -> new NotFoundException("Form " + id + " not found"));
    }
}
