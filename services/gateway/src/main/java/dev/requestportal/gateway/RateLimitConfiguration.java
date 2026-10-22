package dev.requestportal.gateway;

import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class RateLimitConfiguration {

    /**
     * Rate limits per signed-in user (the token's subject), so one user cannot exhaust the API for others.
     * Every API route requires a token, so the IP fallback only matters if that ever changes.
     */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                        .map(address -> "ip:" + address.getAddress().getHostAddress()));
    }
}
