package com.esg.crosscutting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esg.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** RFC 7807 problem details for every kind of error (WP-15, TRD §12.3). */
class ProblemDetailTest extends ApiTestSupport {

  @Test
  void validationErrorsAreProblemDetailsWithFieldErrors() throws Exception {
    mvc.perform(post("/v1/admin/users").header("Authorization", bearerForRole("TENANT_ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nope\",\"firstName\":\"\",\"lastName\":\"B\",\"password\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errorCode").value("ESG-400-VALIDATION"))
        .andExpect(jsonPath("$.instance").value("/v1/admin/users"))
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors.firstName").exists());
  }

  @Test
  void unauthenticatedNotFoundAndForbiddenAreProblemDetails() throws Exception {
    mvc.perform(get("/v1/admin/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("ESG-401"));
    mvc.perform(get("/v1/admin/users/00000000-0000-4000-8000-000000000000").header("Authorization", bearerForRole("TENANT_ADMIN")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.errorCode").value("ESG-404"));
    mvc.perform(get("/v1/admin/users").header("Authorization", bearerForRole("INVESTOR_VIEWER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Insufficient permissions"))
        .andExpect(jsonPath("$.errorCode").value("ESG-403"));
  }
}
