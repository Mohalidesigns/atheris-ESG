package com.esg.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import com.esg.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Field round-trip test generator (WP-18, master plan §7.2).
 *
 * <p>Each form in {@code docs/esg-completion/FIELD_INVENTORY.csv} has a spec in {@code testing/roundtrip/*.json}
 * (valid values, changes, invalid values). From it this class generates, per form:
 * <ol>
 *   <li>create-all-fields: POST every field → 201 → GET → every field equals the input;</li>
 *   <li>update-each-field: change one field at a time → PUT → GET → only that field changed;</li>
 *   <li>validation matrix: each invalid value → 400 with the field named in the problem details;</li>
 *   <li>permission: a read-only role gets 403; field-level masked fields come back null;</li>
 *   <li>tenant isolation: tenant B can't read or change the record.</li>
 * </ol>
 * {@link #inventoryIsFullyCovered()} fails the build when an inventoried field has no spec value, so a new field
 * can't ship without a round-trip test. The frontend reload generator ({@code src/test/roundTripReload.test.jsx})
 * reads the same specs.
 */
class FieldRoundTripTest extends ApiTestSupport {

  static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
  /** Fixture data source and evidence file in tenant A (rls/fixture.sql). */
  static final String SOURCE_A = "a0000000-0000-4000-8000-0000000000c1";
  static final String FILE_A = "a0000000-0000-4000-8000-0000000004f1";
  static final Path SPECS = ROOT.resolve("testing/roundtrip");
  static final Path INVENTORY = ROOT.resolve("docs/esg-completion/FIELD_INVENTORY.csv");
  private static final String COVERED_ELSEWHERE = "covered-elsewhere.json";

  @Autowired private ObjectMapper json;

  // ================================================================== coverage

  @Test
  void inventoryIsFullyCovered() throws Exception {
    Map<String, List<JsonNode>> specsByForm = new LinkedHashMap<>();
    for (JsonNode spec : specs()) {
      spec.path("forms").forEach(f -> specsByForm.computeIfAbsent(f.asText(), k -> new ArrayList<>()).add(spec));
    }
    JsonNode elsewhere = json.readTree(SPECS.resolve(COVERED_ELSEWHERE).toFile());

    List<String> problems = new ArrayList<>();
    Set<String> inventoryForms = new HashSet<>();
    for (Map<String, String> row : inventory()) {
      if (!"WORKS".equals(row.get("status"))) {
        continue;
      }
      String form = row.get("form_name");
      inventoryForms.add(form);
      List<JsonNode> formSpecs = specsByForm.get(form);
      if (formSpecs == null) {
        if (!elsewhere.has(form)) {
          problems.add("form '" + form + "' has no round-trip spec and is not in covered-elsewhere.json");
        }
        continue;
      }
      String property = row.get("dto_property");
      boolean covered = formSpecs.stream().anyMatch(spec -> {
        String field = spec.path("inventoryAliases").path(property).asText(property);
        return spec.path("values").has(field) || spec.path("writeOnly").has(field) || spec.path("derived").has(field)
            || spec.path("coveredBy").has(field);
      });
      if (!covered) {
        problems.add("'" + form + "' field " + property + " has no value in any spec for that form");
      }
    }
    specsByForm.keySet().stream().filter(f -> !inventoryForms.contains(f))
        .forEach(f -> problems.add("spec form '" + f + "' is not a WORKS form in FIELD_INVENTORY.csv"));

    // Hand-written tests named in covered-elsewhere.json (and successCoveredBy) must exist.
    List<String> references = new ArrayList<>();
    elsewhere.properties().forEach(e -> {
      if (!e.getKey().startsWith("_")) {
        e.getValue().forEach(r -> references.add(r.asText()));
      }
    });
    specs().stream().filter(s -> s.has("successCoveredBy")).forEach(s -> references.add(s.path("successCoveredBy").asText()));
    specs().forEach(s -> s.path("coveredBy").forEach(r -> references.add(r.asText())));
    for (String reference : references) {
      String[] parts = reference.split("#");
      try {
        Class<?> type = Class.forName(parts[0]);
        boolean found = Stream.of(type.getDeclaredMethods()).anyMatch(m -> m.getName().equals(parts[1]));
        if (!found) {
          problems.add("referenced test method missing: " + reference);
        }
      } catch (ClassNotFoundException e) {
        problems.add("referenced test class missing: " + reference);
      }
    }
    assertThat(problems).as("FIELD_INVENTORY.csv coverage by testing/roundtrip").isEmpty();
  }

  // ================================================================== generated tests

  @TestFactory
  Stream<DynamicNode> roundTrips() throws IOException {
    return specs().stream().map(spec -> DynamicContainer.dynamicContainer(spec.path("id").asText(), testsFor(spec)));
  }

  private Stream<DynamicNode> testsFor(JsonNode raw) {
    Form form = new Form(raw);
    List<DynamicNode> tests = new ArrayList<>();
    if (form.isCommand()) {
      if (raw.has("success")) {
        tests.add(DynamicTest.dynamicTest("succeeds with valid values", form::commandSucceeds));
      }
    } else {
      tests.add(DynamicTest.dynamicTest("create-all-fields round-trips", form::createAllFields));
      if (raw.has("changes")) {
        tests.add(DynamicTest.dynamicTest("update-each-field changes only that field", form::updateEachField));
      }
      tests.add(DynamicTest.dynamicTest("read-only role " + raw.path("readOnlyRole").asText() + " is denied",
          form::readOnlyRoleIsDenied));
      if (raw.has("masked")) {
        tests.add(DynamicTest.dynamicTest("masked fields for " + raw.path("masked").path("role").asText(), form::maskedFields));
      }
      tests.add(DynamicTest.dynamicTest("tenant isolation", form::tenantIsolation));
    }
    for (JsonNode invalid : raw.path("invalid")) {
      String name = "invalid " + invalid.path("field").asText() + " (" + invalid.path("rule").asText() + ") → 400";
      tests.add(DynamicTest.dynamicTest(name, () -> form.rejects(invalid)));
    }
    return tests.stream();
  }

  /** One spec, with its placeholders ({u}, {yyyy}, fixture ids) resolved once. */
  private final class Form {
    private final JsonNode spec;
    private final Map<String, JsonNode> values = new LinkedHashMap<>();
    private final Map<String, JsonNode> changes = new LinkedHashMap<>();
    private String unique;
    private String year;

    Form(JsonNode spec) {
      this.spec = spec;
      rotate();
    }

    /** New unique values ({u}, {yyyy}) so the next record doesn't collide with the last (slugs, emails, periods). */
    private void rotate() {
      unique = UUID.randomUUID().toString().substring(0, 8);
      year = String.valueOf(ThreadLocalRandom.current().nextInt(2200, 2999));
      values.clear();
      changes.clear();
      spec.path("values").properties().forEach(e -> values.put(e.getKey(), resolve(e.getValue())));
      spec.path("changes").properties().forEach(e -> changes.put(e.getKey(), resolve(e.getValue())));
    }

    boolean isCommand() {
      return "command".equals(spec.path("kind").asText());
    }

    boolean singleton() {
      return spec.path("singleton").asBoolean();
    }

    String actor() {
      return bearerForRole(spec.path("actor").asText());
    }

    // ---------------------------------------------------------------- the six patterns

    void createAllFields() throws Exception {
      if (singleton()) {
        withSingletonRestored(() -> {
          put(Map.copyOf(values));
          assertFields(read(null), values, "after saving every field");
        });
        return;
      }
      Created created = create();
      JsonNode saved = read(created.id());
      assertFields(saved, created.values(), "after create");
      spec.path("derived").properties().forEach(e ->
          assertThat(same(e.getValue(), at(saved, e.getKey())))
              .as("derived %s: expected %s but was %s", e.getKey(), e.getValue(), at(saved, e.getKey())).isTrue());
    }

    void updateEachField() throws Exception {
      if (singleton()) {
        withSingletonRestored(() -> {
          Map<String, JsonNode> current = new LinkedHashMap<>(values);
          put(current);
          for (Map.Entry<String, JsonNode> change : changes.entrySet()) {
            current.put(change.getKey(), change.getValue());
            put(current);
            assertFields(read(null), current, "after changing only " + change.getKey());
          }
        });
        return;
      }
      Created created = create();
      String id = created.id();
      Map<String, JsonNode> current = new LinkedHashMap<>(created.values());
      for (Map.Entry<String, JsonNode> change : created.changes().entrySet()) {
        current.put(change.getKey(), change.getValue());
        update(id, current);
        assertFields(read(id), current, "after changing only " + change.getKey());
      }
    }

    void rejects(JsonNode invalid) throws Exception {
      String field = invalid.path("field").asText();
      String errorField = invalid.path("errorField").asText(field);
      Map<String, JsonNode> body = new LinkedHashMap<>(values);
      body.put(field, resolve(invalid.path("value")));
      MockHttpServletRequestBuilder call;
      if (isCommand()) {
        call = call(spec.path("command"), null, body(body));
      } else if (singleton()) {
        ObjectNode b = body(body);
        b.put("version", read(null).path("version").asLong());
        call = call(spec.path("update"), null, b);
      } else {
        call = call(spec.path("create"), null, body(body));
      }
      if (!spec.path("command").path("anonymous").asBoolean()) {
        call.header("Authorization", actor());
      }
      MvcResult result = mvc.perform(call).andReturn();
      assertThat(result.getResponse().getStatus()).as("%s=%s → status (%s)", field, invalid.path("value"),
          result.getResponse().getContentAsString()).isEqualTo(400);
      JsonNode errors = json.readTree(result.getResponse().getContentAsString()).path("errors");
      assertThat(errors.has(errorField)).as("problem details name '%s': %s", errorField, errors).isTrue();
    }

    void readOnlyRoleIsDenied() throws Exception {
      String role = bearerForRole(spec.path("readOnlyRole").asText());
      if (singleton()) {
        ObjectNode b = body(values);
        b.put("version", read(null).path("version").asLong());
        mvc.perform(call(spec.path("update"), null, b).header("Authorization", role)).andExpect(status().isForbidden());
        return;
      }
      mvc.perform(call(spec.path("create"), null, body(fresh())).header("Authorization", role))
          .andExpect(status().isForbidden());
      if (spec.has("update")) {
        Created created = create();
        String id = created.id();
        ObjectNode b = updateBody(created.values(), read(id));
        mvc.perform(call(spec.path("update"), id, b).header("Authorization", role)).andExpect(status().isForbidden());
      }
    }

    void maskedFields() throws Exception {
      String id = create().id();
      String role = bearerForRole(spec.path("masked").path("role").asText());
      JsonNode asMasked = data(mvc.perform(get(path(spec.path("read"), id)).header("Authorization", role))
          .andExpect(status().isOk()).andReturn());
      for (JsonNode field : spec.path("masked").path("fields")) {
        assertThat(at(asMasked, field.asText()).isNull()).as("%s masked for %s", field.asText(),
            spec.path("masked").path("role").asText()).isTrue();
        assertThat(at(read(id), field.asText()).isNull()).as("%s visible to the actor", field.asText()).isFalse();
      }
    }

    void tenantIsolation() throws Exception {
      String other = bearer(USER_B_ADMIN, PostgresTestDatabase.TENANT_B);
      if (singleton()) {
        JsonNode before = data(mvc.perform(get(spec.path("read").path("path").asText()).header("Authorization", other))
            .andReturn());
        withSingletonRestored(() -> {
          put(Map.copyOf(values));
          JsonNode after = data(mvc.perform(get(spec.path("read").path("path").asText()).header("Authorization", other))
              .andReturn());
          assertThat(after).as("tenant B's settings are untouched by tenant A").isEqualTo(before);
        });
        return;
      }
      Created created = create();
      String id = created.id();
      int expected = spec.path("isolation").path("status").asInt(404);
      if (spec.path("read").path("list").asBoolean()) {
        MvcResult list = mvc.perform(get(spec.path("read").path("path").asText()).header("Authorization", other)).andReturn();
        if (expected == 403) {
          assertThat(list.getResponse().getStatus()).isEqualTo(403);
        } else {
          assertThat(find(data(list), id)).as("tenant B's list must not contain tenant A's record").isNull();
        }
      } else {
        mvc.perform(get(path(spec.path("read"), id)).header("Authorization", other))
            .andExpect(status().is(expected));
      }
      if (spec.has("update")) {
        ObjectNode b = updateBody(created.values(), read(id));
        mvc.perform(call(spec.path("update"), id, b).header("Authorization", other)).andExpect(status().is(expected));
        assertFields(read(id), created.values(), "after tenant B's update attempt");
      }
    }

    void commandSucceeds() throws Exception {
      MockHttpServletRequestBuilder call = call(spec.path("command"), null, body(values));
      MvcResult result = mvc.perform(call)
          .andExpect(status().is(spec.path("success").path("status").asInt(200))).andReturn();
      JsonNode body = json.readTree(result.getResponse().getContentAsString());
      for (JsonNode path : spec.path("success").path("exists")) {
        assertThat(at(body, path.asText()).isMissingNode() || at(body, path.asText()).isNull())
            .as("response has %s", path.asText()).isFalse();
      }
    }

    // ---------------------------------------------------------------- HTTP helpers

    /** Values with fresh unique placeholders, for a record that is never created. */
    private Map<String, JsonNode> fresh() {
      return new Form(spec).values;
    }

    /** Creates a record from the current values, then rotates the unique values for the next create. */
    private Created create() throws Exception {
      Created used = new Created(null, new LinkedHashMap<>(values), new LinkedHashMap<>(changes));
      MvcResult result = mvc.perform(call(spec.path("create"), null, body(values)).header("Authorization", actor()))
          .andReturn();
      assertThat(result.getResponse().getStatus()).as("create %s with %s: %s", spec.path("id").asText(), body(values),
          result.getResponse().getContentAsString()).isEqualTo(201);
      String id = data(result).path("id").asText();
      rotate();
      return new Created(id, used.values(), used.changes());
    }

    private void update(String id, Map<String, JsonNode> current) throws Exception {
      ObjectNode b = updateBody(current, read(id));
      MvcResult result = mvc.perform(call(spec.path("update"), id, b).header("Authorization", actor())).andReturn();
      assertThat(result.getResponse().getStatus()).as("update %s: %s", spec.path("id").asText(),
          result.getResponse().getContentAsString()).isEqualTo(200);
    }

    private ObjectNode updateBody(Map<String, JsonNode> current, JsonNode saved) {
      Map<String, JsonNode> fields = new LinkedHashMap<>();
      JsonNode allowed = spec.path("update").path("fields");
      current.forEach((k, v) -> {
        if (allowed.isMissingNode() || containsText(allowed, k)) {
          fields.put(k, v);
        }
      });
      ObjectNode b = body(fields);
      if (spec.path("update").path("versioned").asBoolean()) {
        b.put("version", saved.path("version").asLong());
      }
      return b;
    }

    private void put(Map<String, JsonNode> current) throws Exception {
      ObjectNode b = body(current);
      b.put("version", read(null).path("version").asLong());
      MvcResult result = mvc.perform(call(spec.path("update"), null, b).header("Authorization", actor())).andReturn();
      assertThat(result.getResponse().getStatus()).as("save %s: %s", spec.path("id").asText(),
          result.getResponse().getContentAsString()).isEqualTo(200);
    }

    private void withSingletonRestored(ThrowingRunnable body) throws Exception {
      JsonNode original = read(null);
      try {
        body.run();
      } finally {
        Map<String, JsonNode> restore = new LinkedHashMap<>();
        values.keySet().forEach(k -> restore.put(k, at(original, k)));
        put(restore);
      }
    }

    private JsonNode read(String id) throws Exception {
      JsonNode readSpec = spec.path("read");
      MvcResult result = mvc.perform(get(path(readSpec, id)).header("Authorization", actor()))
          .andExpect(status().isOk()).andReturn();
      JsonNode data = data(result);
      if (readSpec.path("list").asBoolean()) {
        JsonNode found = find(data, id);
        assertThat(found).as("record %s in %s", id, readSpec.path("path").asText()).isNotNull();
        return found;
      }
      return data;
    }

    private String path(JsonNode endpoint, String id) {
      String path = endpoint.path("path").asText();
      return id == null ? path : path.replace("{id}", id);
    }

    private MockHttpServletRequestBuilder call(JsonNode endpoint, String id, JsonNode body) throws IOException {
      return request(HttpMethod.valueOf(endpoint.path("method").asText("POST")), path(endpoint, id))
          .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    // ---------------------------------------------------------------- values

    private JsonNode resolve(JsonNode node) {
      if (node == null || node.isMissingNode()) {
        return JsonNodeFactory.instance.nullNode();
      }
      if (node.isTextual()) {
        return JsonNodeFactory.instance.textNode(node.asText()
            .replace("{u}", unique).replace("{yyyy}", year)
            .replace("{ORG_HQ}", ORG_HQ).replace("{ORG_SITE_1}", ORG_SITE_1).replace("{ORG_SITE_2}", ORG_SITE_2)
            .replace("{SOURCE_A}", SOURCE_A).replace("{FILE_A}", FILE_A)
            .replace("{USER_COMPLIANCE}", roleUserId("COMPLIANCE_OFFICER")).replace("{USER_DIRECTOR}", roleUserId("ESG_DIRECTOR"))
            .replace("{USER_MANAGER}", roleUserId("ESG_MANAGER")).replace("{PROJECT_A}", "a0000000-0000-4000-8000-000000000301")
            .replace("{TEMPLATE_A}", "a0000000-0000-4000-8000-0000000000f4"));
      }
      if (node.isObject() && node.has("$repeat")) {
        return JsonNodeFactory.instance.textNode(node.path("$repeat").get(0).asText().repeat(node.path("$repeat").get(1).asInt()));
      }
      if (node.isArray()) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        node.forEach(n -> array.add(resolve(n)));
        return array;
      }
      return node;
    }

    private void assertFields(JsonNode saved, Map<String, JsonNode> expected, String when) {
      expected.forEach((field, value) -> {
        if (spec.path("writeOnly").has(field)) {
          assertThat(at(saved, field).isMissingNode() || at(saved, field).isNull())
              .as("write-only %s must never be returned (%s)", field, when).isTrue();
          return;
        }
        // readAliases: a field the API returns under another name (e.g. a converted amount's original value).
        String readAs = spec.path("readAliases").path(field).asText(field);
        assertThat(same(value, at(saved, readAs)))
            .as("%s.%s %s: expected %s but was %s", spec.path("id").asText(), field, when, value, at(saved, readAs))
            .isTrue();
      });
    }
  }

  private record Created(String id, Map<String, JsonNode> values, Map<String, JsonNode> changes) {}

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  // ================================================================== JSON helpers

  /** Equality the way a user sees it: 5.60 = 5.6, missing = null, multi-selects ignore order. */
  static boolean same(JsonNode expected, JsonNode actual) {
    boolean expectedEmpty = expected == null || expected.isNull() || expected.isMissingNode();
    boolean actualEmpty = actual == null || actual.isNull() || actual.isMissingNode();
    if (expectedEmpty || actualEmpty) {
      return expectedEmpty && actualEmpty;
    }
    if (expected.isNumber() || actual.isNumber()) {
      try {
        return new BigDecimal(expected.asText()).compareTo(new BigDecimal(actual.asText())) == 0;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    if (expected.isObject() && actual.isObject()) {
      // e.g. a gas composition: same keys with the same values (numbers compared numerically)
      java.util.Set<String> keys = new java.util.HashSet<>();
      expected.fieldNames().forEachRemaining(keys::add);
      actual.fieldNames().forEachRemaining(keys::add);
      return keys.stream().allMatch(k -> same(expected.get(k), actual.get(k)));
    }
    if (expected.isArray() && actual.isArray()) {
      if (expected.size() != actual.size()) {
        return false;
      }
      if (expected.size() > 0 && expected.get(0).isObject()) {
        // e.g. workforce lines: every expected row has an equal row in the response (order ignored)
        List<JsonNode> left = new ArrayList<>();
        actual.forEach(left::add);
        for (JsonNode e : expected) {
          JsonNode match = left.stream().filter(a -> same(e, a)).findFirst().orElse(null);
          if (match == null) {
            return false;
          }
          left.remove(match);
        }
        return true;
      }
      List<String> a = new ArrayList<>();
      List<String> b = new ArrayList<>();
      expected.forEach(n -> a.add(n.asText()));
      actual.forEach(n -> b.add(n.asText()));
      return a.containsAll(b) && b.containsAll(a);
    }
    return expected.asText().equals(actual.asText());
  }

  /** Dotted path ("admin.email") into a node. */
  static JsonNode at(JsonNode node, String dotted) {
    JsonNode current = node;
    for (String part : dotted.split("\\.")) {
      current = current.path(part);
    }
    return current;
  }

  /** Flat map with dotted keys → nested JSON body. */
  private static ObjectNode body(Map<String, JsonNode> fields) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    fields.forEach((key, value) -> {
      String[] parts = key.split("\\.");
      ObjectNode target = root;
      for (int i = 0; i < parts.length - 1; i++) {
        target = target.has(parts[i]) ? (ObjectNode) target.get(parts[i]) : target.putObject(parts[i]);
      }
      target.set(parts[parts.length - 1], value);
    });
    return root;
  }

  private static boolean containsText(JsonNode array, String value) {
    for (JsonNode n : array) {
      if (n.asText().equals(value)) {
        return true;
      }
    }
    return false;
  }

  private JsonNode data(MvcResult result) throws IOException {
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  /** The element with this id in a list or a page ({@code content}). */
  private static JsonNode find(JsonNode data, String id) {
    JsonNode items = data.has("content") ? data.path("content") : data;
    for (JsonNode item : items) {
      if (id.equals(item.path("id").asText())) {
        return item;
      }
    }
    return null;
  }

  // ================================================================== inputs

  List<JsonNode> specs() throws IOException {
    List<JsonNode> specs = new ArrayList<>();
    try (Stream<Path> files = Files.list(SPECS)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
        if (!file.getFileName().toString().equals(COVERED_ELSEWHERE)) {
          specs.add(json.readTree(file.toFile()));
        }
      }
    }
    return specs;
  }

  /** FIELD_INVENTORY.csv rows as header → value (RFC 4180 quoting). */
  static List<Map<String, String>> inventory() throws IOException {
    List<List<String>> rows = parseCsv(Files.readString(INVENTORY, StandardCharsets.UTF_8));
    List<String> header = rows.get(0);
    List<Map<String, String>> result = new ArrayList<>();
    for (List<String> row : rows.subList(1, rows.size())) {
      Map<String, String> map = new LinkedHashMap<>();
      for (int i = 0; i < header.size(); i++) {
        map.put(header.get(i), i < row.size() ? row.get(i) : "");
      }
      result.add(map);
    }
    return result;
  }

  static List<List<String>> parseCsv(String text) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          cell.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        row.add(cell.toString());
        cell.setLength(0);
      } else if (c == '\n') {
        row.add(cell.toString());
        cell.setLength(0);
        if (!(row.size() == 1 && row.get(0).isBlank())) {
          rows.add(row);
        }
        row = new ArrayList<>();
      } else if (c != '\r') {
        cell.append(c);
      }
    }
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }
}
