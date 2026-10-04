package com.esg.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** WP-46 (D44): production webhooks go to public HTTPS addresses only (request forgery). */
class WebhookUrlPolicyTest {

  private final WebhookUrlPolicy production = new WebhookUrlPolicy(false, false);

  @Test
  void onlyPublicHttpsAddressesInProduction() {
    assertThat(production.problem("http://hooks.example.com/esg")).contains("must start with https://");
    assertThat(production.problem("ftp://hooks.example.com")).contains("must start with https://");
    assertThat(production.problem("https://127.0.0.1/hook")).contains("must be a public address (private and local networks are not allowed)");
    assertThat(production.problem("https://localhost/hook")).isPresent();
    assertThat(production.problem("https://10.1.2.3/hook")).isPresent();
    assertThat(production.problem("https://192.168.0.10/hook")).isPresent();
    assertThat(production.problem("https://169.254.169.254/latest/meta-data")).isPresent();
    assertThat(production.problem("https://[::1]/hook")).isPresent();
    assertThat(production.problem("https://[fd00::1]/hook")).isPresent();
    assertThat(production.problem("https://user:secret@93.184.216.34/hook")).contains("must not contain a user name or password");
    assertThat(production.problem("not a url")).isPresent();
    assertThat(production.problem("https://93.184.216.34/hook")).isEmpty();
  }

  @Test
  void developmentMayUseLocalReceivers() {
    WebhookUrlPolicy local = new WebhookUrlPolicy(true, true);
    assertThat(local.problem("http://localhost:9999/hook")).isEmpty();
    assertThat(local.problem("https://127.0.0.1/hook")).isEmpty();
  }

  @Test
  void signatureIsHmacSha256OfTimestampDotBody() {
    // Golden value computed independently (Python hmac): HMAC-SHA256("whsec_test", "1700000000.{}").
    assertThat(WebhookDispatcher.hmac("whsec_test", "1700000000.{}"))
        .isEqualTo("35495024f4ef3f94e5a93e22221544c4b75e9a42300cd965ab81cb85cd994e91");
    assertThat(WebhookDispatcher.hmac("whsec_other", "1700000000.{}")).isNotEqualTo(WebhookDispatcher.hmac("whsec_test", "1700000000.{}"));
  }
}
