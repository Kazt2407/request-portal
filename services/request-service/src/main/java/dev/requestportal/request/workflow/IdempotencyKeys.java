package dev.requestportal.request.workflow;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Remembers which request an {@code Idempotency-Key} produced, per requester. */
@Component
class IdempotencyKeys {

    record Entry(String requestHash, long requestId) {
    }

    private final JdbcClient jdbc;

    IdempotencyKeys(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Entry> find(UUID requesterId, String key) {
        return jdbc.sql("""
                        select request_hash, request_id from idempotency_key
                        where requester_id = :requester and idempotency_key = :key
                        """)
                .param("requester", requesterId)
                .param("key", key)
                .query((row, rowNumber) -> new Entry(row.getString("request_hash"), row.getLong("request_id")))
                .optional();
    }

    /** Fails with a duplicate-key error if another submission with the same key committed first. */
    void save(UUID requesterId, String key, String requestHash, long requestId) {
        jdbc.sql("""
                        insert into idempotency_key (requester_id, idempotency_key, request_hash, request_id)
                        values (:requester, :key, :hash, :request)
                        """)
                .param("requester", requesterId)
                .param("key", key)
                .param("hash", requestHash)
                .param("request", requestId)
                .update();
    }
}
