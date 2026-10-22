package dev.requestportal.request.workflow;

import dev.requestportal.request.template.RequestTemplate;
import dev.requestportal.request.template.RequestTemplateRepository;
import dev.requestportal.request.template.TemplateStatus;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.web.BusinessRuleException;
import dev.requestportal.web.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Submits requests. With an {@code Idempotency-Key}, retrying the same submission (a double click, a
 * timeout followed by a retry) returns the original request instead of creating a second one.
 */
@Service
public class SubmissionService {

    /** @param replayed true when an earlier submission with the same idempotency key was returned */
    public record Result(RequestViews.Detail request, boolean replayed) {
    }

    private final RequestTemplateRepository templates;
    private final ServiceRequestRepository requests;
    private final FieldValueValidator validator;
    private final RequestNumbers requestNumbers;
    private final IdempotencyKeys idempotencyKeys;
    private final RequestQueryService queries;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SubmissionService(
            RequestTemplateRepository templates,
            ServiceRequestRepository requests,
            FieldValueValidator validator,
            RequestNumbers requestNumbers,
            IdempotencyKeys idempotencyKeys,
            RequestQueryService queries,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.templates = templates;
        this.requests = requests;
        this.validator = validator;
        this.requestNumbers = requestNumbers;
        this.idempotencyKeys = idempotencyKeys;
        this.queries = queries;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Result submit(RequestBodies.Submit body, String idempotencyKey, CurrentUser user) {
        if (idempotencyKey == null) {
            return transaction.execute(status -> create(body, null, null, user));
        }
        String fingerprint = fingerprint(body);
        Optional<Result> earlier = replay(idempotencyKey, fingerprint, user);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        try {
            return transaction.execute(status -> create(body, idempotencyKey, fingerprint, user));
        } catch (DataIntegrityViolationException exception) {
            // A concurrent submission with the same key committed first; this one was rolled back.
            return replay(idempotencyKey, fingerprint, user).orElseThrow(() -> exception);
        }
    }

    private Result create(RequestBodies.Submit body, String idempotencyKey, String fingerprint, CurrentUser user) {
        RequestTemplate template = templates.findById(body.templateId())
                .filter(found -> found.getStatus() == TemplateStatus.ACTIVE && found.getAudience().admits(user))
                .orElseThrow(() -> new NotFoundException("Template " + body.templateId() + " is not available to you"));
        var values = validator.validate(template, body.values());
        Instant now = clock.instant();

        ServiceRequest request = ServiceRequest.submit(
                requestNumbers.nextCode(template.getDepartmentCode()),
                template,
                Actor.of(user),
                user.email(),
                values,
                now);
        requests.saveAndFlush(request);
        if (idempotencyKey != null) {
            idempotencyKeys.save(user.id(), idempotencyKey, fingerprint, request.getId());
        }
        return new Result(RequestViews.Detail.of(request, now), false);
    }

    private Optional<Result> replay(String idempotencyKey, String fingerprint, CurrentUser user) {
        return transaction.execute(status -> idempotencyKeys.find(user.id(), idempotencyKey).map(entry -> {
            if (!entry.requestHash().equals(fingerprint)) {
                throw new BusinessRuleException("Idempotency-Key " + idempotencyKey
                        + " was already used for a different submission");
            }
            return new Result(queries.detail(entry.requestId(), user), true);
        }));
    }

    /** SHA-256 of the template and values, independent of the order the values were sent in. */
    private static String fingerprint(RequestBodies.Submit body) {
        String canonical = body.templateId() + "|" + new TreeMap<>(body.values());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
