package com.esg.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * D47: CVE-2026-47884 (XsltView) and CVE-2026-47890 (Server-Sent Events with views) in Spring Framework 6.2.19 are
 * suppressed in {@code .trivyignore} because the application uses neither feature. These rules keep that true until the
 * Spring Boot 4 migration (WP-55): using either feature fails the build, and the suppression must be lifted first.
 */
class UnreachableSpringCveArchTest {

  private static final JavaClasses CLASSES = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("com.esg");

  @Test
  void noXsltViews() {
    ArchRule rule = noClasses()
        .should().dependOnClassesThat().resideInAPackage("org.springframework.web.servlet.view.xslt..")
        .because("CVE-2026-47884 (XsltView) is suppressed in .trivyignore on the basis that no XSLT view is used (D47)");
    rule.check(CLASSES);
  }

  @Test
  void noServerSentEvents() {
    ArchRule rule = noClasses()
        .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
        .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter")
        .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.http.codec.ServerSentEvent")
        .because("CVE-2026-47890 (Server-Sent Events) is suppressed in .trivyignore on the basis that no SSE is streamed (D47)");
    rule.check(CLASSES);
  }
}
