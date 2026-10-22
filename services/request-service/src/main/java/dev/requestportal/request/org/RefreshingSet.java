package dev.requestportal.request.org;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A small cache for reference data that rarely changes. Entries live for {@code maxAge}; an unknown value
 * triggers one early reload (at most every {@code minRefreshInterval}), so data added a moment ago in
 * org-service is accepted without waiting for the cache to expire.
 */
final class RefreshingSet {

    private final Supplier<Set<String>> loader;
    private final Duration maxAge;
    private final Duration minRefreshInterval;
    private final Clock clock;
    private volatile Snapshot snapshot;

    private record Snapshot(Set<String> values, Instant loadedAt) {
    }

    RefreshingSet(Supplier<Set<String>> loader, Duration maxAge, Duration minRefreshInterval, Clock clock) {
        this.loader = loader;
        this.maxAge = maxAge;
        this.minRefreshInterval = minRefreshInterval;
        this.clock = clock;
    }

    boolean contains(String value) {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current == null || current.loadedAt().plus(maxAge).isBefore(now)) {
            current = reload();
        }
        if (!current.values().contains(value) && current.loadedAt().plus(minRefreshInterval).isBefore(now)) {
            current = reload();
        }
        return current.values().contains(value);
    }

    private synchronized Snapshot reload() {
        Snapshot loaded = new Snapshot(Set.copyOf(loader.get()), clock.instant());
        snapshot = loaded;
        return loaded;
    }
}
