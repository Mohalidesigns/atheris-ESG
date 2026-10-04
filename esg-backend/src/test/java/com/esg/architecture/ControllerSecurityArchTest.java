package com.esg.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.esg.security.PublicEndpoint;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every REST endpoint must declare its authorisation (WP-13, TRD §5.3.2, AS-IS S1): either
 * {@code @PreAuthorize} or an explicit {@code @PublicEndpoint("reason")}. A new endpoint without either fails the build.
 */
class ControllerSecurityArchTest {

  private static final JavaClasses CLASSES = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("com.esg");

  @Test
  void everyEndpointDeclaresItsAuthorisation() {
    ArchRule rule = methods()
        .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
        .and().areAnnotatedWith(GetMapping.class)
        .or().areAnnotatedWith(PostMapping.class)
        .or().areAnnotatedWith(PutMapping.class)
        .or().areAnnotatedWith(DeleteMapping.class)
        .or().areAnnotatedWith(PatchMapping.class)
        .or().areAnnotatedWith(RequestMapping.class)
        .should().beAnnotatedWith(PreAuthorize.class)
        .orShould().beAnnotatedWith(PublicEndpoint.class)
        .because("every endpoint needs a permission check or an explicit, justified public marker");
    rule.check(CLASSES);
  }
}
