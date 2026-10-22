package dev.requestportal.request;

import dev.requestportal.security.EnableServiceSecurity;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableServiceSecurity
public class RequestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequestServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Time zone for calendar-day filters such as "submitted on 2026-10-22". */
    @Bean
    ZoneId portalZone(@Value("${portal.time-zone}") String zone) {
        return ZoneId.of(zone);
    }
}
