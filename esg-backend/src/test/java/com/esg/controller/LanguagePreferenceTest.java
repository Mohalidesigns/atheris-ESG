package com.esg.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** WP-45 (D45): anyone signed in keeps their interface language on their account. */
class LanguagePreferenceTest extends ApiTestSupport {

  @Test
  void aUserChoosesTheirOwnLanguage() throws Exception {
    String auditor = bearerForRole("AUDITOR");
    mvc.perform(put("/v1/auth/me/language").header("Authorization", auditor).contentType(MediaType.APPLICATION_JSON)
        .content("{\"preferredLanguage\":\"ar\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.preferredLanguage").value("ar"));
    mvc.perform(get("/v1/auth/me").header("Authorization", auditor)).andExpect(jsonPath("$.data.preferredLanguage").value("ar"));
    mvc.perform(put("/v1/auth/me/language").header("Authorization", auditor).contentType(MediaType.APPLICATION_JSON)
        .content("{\"preferredLanguage\":\"de\"}")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.preferredLanguage").value("must be one of en, fr, pt, ar, sw, ha, yo"));
    mvc.perform(put("/v1/auth/me/language").contentType(MediaType.APPLICATION_JSON).content("{\"preferredLanguage\":\"fr\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(put("/v1/auth/me/language").header("Authorization", auditor).contentType(MediaType.APPLICATION_JSON)
        .content("{\"preferredLanguage\":\"en\"}")).andExpect(status().isOk());
  }
}
