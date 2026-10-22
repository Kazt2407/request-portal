package dev.requestportal.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Registers {@link ApiExceptionHandler}. Runs before Spring MVC's auto-configuration so that Boot's own
 * problem-details handler backs off instead of handling validation errors first.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ApiExceptionHandler.class)
public class ServiceWebAutoConfiguration {
}
