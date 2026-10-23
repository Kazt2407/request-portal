package dev.requestportal.web;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.DeserializationFeature;

/**
 * Registers {@link ApiExceptionHandler}, JSON settings and observation filters shared by all services. Runs
 * before Spring MVC's auto-configuration so that Boot's own problem-details handler backs off instead of
 * handling validation errors first.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ApiExceptionHandler.class)
public class ServiceWebAutoConfiguration {

    /** Jackson 3 rejects an omitted boolean such as {@code "required"}; in our APIs it means false. */
    @Bean
    JsonMapperBuilderCustomizer omittedBooleansAreFalse() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObservationPredicate.class)
    static class ObservationConfiguration {

        @Bean
        ObservationPredicate skipActuatorObservations() {
            return ActuatorObservationFilter.skipActuator();
        }
    }
}
