package dev.requestportal.gateway;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {

    /** Health and info checks stay out of traces and request metrics; probes would drown out real traffic. */
    @Bean
    ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getPath().value().startsWith("/actuator"))
                && !(name.startsWith("spring.security.") && context.getParentObservation() == null);
    }
}
