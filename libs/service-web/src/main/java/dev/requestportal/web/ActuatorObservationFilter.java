package dev.requestportal.web;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationView;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Leaves health and info checks out of traces and request metrics: Kubernetes probes every few seconds
 * and would otherwise drown out real traffic.
 */
final class ActuatorObservationFilter {

    private ActuatorObservationFilter() {
    }

    static ObservationPredicate skipActuator() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"))
                // Spring Security's own observations are only useful inside a request's trace; without a
                // parent they belong to a skipped probe and would show up as separate traces.
                && !(name.startsWith("spring.security.") && isMissing(context.getParentObservation()));
    }

    /** A skipped request still hands its no-op observation to Spring Security as the parent. */
    private static boolean isMissing(ObservationView parent) {
        return parent == null || (parent instanceof Observation observation && observation.isNoop());
    }
}
