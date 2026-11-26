package com.esg.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.mail.MailHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * D50: email is optional, so an SMTP server that is unreachable or has no password must not turn the health endpoint
 * (used by deploy.sh and the container health check) DOWN.
 */
class MailHealthTest {

  @Test
  void applicationYmlTurnsTheMailHealthCheckOff() throws Exception {
    PropertySource<?> yml = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).get(0);
    assertThat(yml.getProperty("management.health.mail.enabled")).isEqualTo(false);
  }

  @Test
  void noMailHealthContributorEvenWithAnSmtpHostAndNoPassword() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class,
            HealthContributorAutoConfiguration.class, MailHealthContributorAutoConfiguration.class))
        .withPropertyValues("spring.mail.host=smtp.example.org", "spring.mail.username=no-reply@example.org",
            "management.health.mail.enabled=false")
        .run((ctx) -> {
          assertThat(ctx).hasBean("mailSender");
          assertThat(ctx).doesNotHaveBean("mailHealthContributor");
        });
  }

  @Test
  void theCheckWouldOtherwiseRun() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class,
            HealthContributorAutoConfiguration.class, MailHealthContributorAutoConfiguration.class))
        .withPropertyValues("spring.mail.host=smtp.example.org")
        .run((ctx) -> assertThat(ctx).hasBean("mailHealthContributor"));
  }
}
