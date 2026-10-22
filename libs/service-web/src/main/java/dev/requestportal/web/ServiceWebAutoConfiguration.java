package dev.requestportal.web;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers {@link ApiExceptionHandler}. Runs before Spring MVC's auto-configuration so that Boot's own
 * problem-details handler backs off instead of handling validation errors first.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ApiExceptionHandler.class)
public class ServiceWebAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObservationPredicate.class)
    static class ObservationConfiguration {

        @Bean
        ObservationPredicate skipActuatorObservations() {
            return ActuatorObservationFilter.skipActuator();
        }
    }
}
