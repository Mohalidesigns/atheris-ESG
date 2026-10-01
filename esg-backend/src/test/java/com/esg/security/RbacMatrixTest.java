package com.esg.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RBAC matrix (WP-13, TRD §5.2, §11.2.1): each of the 12 built-in roles × representative endpoints.
 * The expected access is written out here as the specification; the permissions themselves come from
 * R__permission_catalogue.sql, so a catalogue change that breaks this table fails the build.
 */
class RbacMatrixTest extends ApiTestSupport {

  /** Endpoint key → request. Writes use valid bodies so the only possible denial is authorisation. */
  private static final Map<String, java.util.function.Supplier<MockHttpServletRequestBuilder>> ENDPOINTS = Map.of(
      "data:read", () -> get("/v1/data/points"),
      "data:create", () -> post("/v1/data/points").contentType(MediaType.APPLICATION_JSON).content(
          "{\"orgId\":\"" + ORG_SITE_1 + "\",\"category\":\"E\",\"subCategory\":\"Energy\",\"metricKey\":\"electricity_kwh\","
              + "\"numericValue\":1,\"unit\":\"kWh\",\"reportingPeriodStart\":\"2026-04-01\",\"reportingPeriodEnd\":\"2026-06-30\"}"),
      "data:approve", () -> put("/v1/data/points/" + HQ_DATA_POINT + "/validate")
          .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"validated\"}"),
      "carbon:read", () -> get("/v1/carbon/emissions"),
      "compliance:read", () -> get("/v1/compliance/frameworks"),
      "users:read", () -> get("/v1/admin/users"),
      "roles:read", () -> get("/v1/admin/roles"),
      "orgs:read", () -> get("/v1/organisations"),
      "reference:read", () -> get("/v1/reference/metrics"),
      "me", () -> get("/v1/auth/me"));

  private static final Set<String> ALL = ENDPOINTS.keySet();

  /** Role → endpoints it may call (everything else must be 403). */
  private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
      Map.entry("SUPER_ADMIN", ALL),
      Map.entry("TENANT_ADMIN", ALL),
      Map.entry("ESG_DIRECTOR", ALL),
      Map.entry("ESG_MANAGER", Set.of("reference:read", "data:read", "data:create", "carbon:read", "compliance:read", "orgs:read", "me")),
      Map.entry("ESG_ANALYST", Set.of("reference:read", "data:read", "data:create", "carbon:read", "compliance:read", "orgs:read", "me")),
      Map.entry("DATA_COLLECTOR", Set.of("reference:read", "data:read", "data:create", "carbon:read", "orgs:read", "me")),
      Map.entry("AUDITOR", Set.of("reference:read", "data:read", "carbon:read", "compliance:read", "orgs:read", "me")),
      Map.entry("INVESTOR_VIEWER", Set.of("reference:read", "me")),
      Map.entry("CONSULTANT", Set.of("reference:read", "data:read", "data:create", "carbon:read", "compliance:read", "orgs:read", "me")),
      Map.entry("BOARD_MEMBER", Set.of("reference:read", "me")),
      Map.entry("COMPLIANCE_OFFICER", Set.of("reference:read", "data:read", "carbon:read", "compliance:read", "orgs:read", "me")),
      Map.entry("TRAINING_ADMIN", Set.of("reference:read", "users:read", "me")));

  static Stream<Arguments> matrix() {
    return ROLES.stream().flatMap(role -> ALL.stream().sorted()
        .map(endpoint -> Arguments.of(role, endpoint, ALLOWED.get(role).contains(endpoint))));
  }

  @ParameterizedTest(name = "{0} → {1} allowed={2}")
  @MethodSource("matrix")
  void roleCanCallOnlyPermittedEndpoints(String role, String endpoint, boolean allowed) throws Exception {
    var result = mvc.perform(ENDPOINTS.get(endpoint).get().header("Authorization", bearerForRole(role)));
    if (allowed) {
      result.andExpect(status().is2xxSuccessful());
    } else {
      result.andExpect(status().isForbidden());
    }
  }
}
