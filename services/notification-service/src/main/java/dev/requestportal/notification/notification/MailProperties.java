package dev.requestportal.notification.notification;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param from      sender address
 * @param portalUrl base URL of the web app, for links in emails
 * @param timeZone  time zone for dates shown in emails
 */
@ConfigurationProperties("portal.mail")
public record MailProperties(String from, String portalUrl, ZoneId timeZone) {
}
