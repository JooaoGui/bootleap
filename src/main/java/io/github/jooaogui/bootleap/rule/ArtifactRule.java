package io.github.jooaogui.bootleap.rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.jooaogui.bootleap.scan.Dependency;
import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * Artifacts that no longer exist in Spring Boot 4. Without a version from the Spring Boot BOM,
 * the build cannot even resolve them.
 *
 * <p>Checked against the spring-boot-dependencies BOMs of 3.5.16 and 4.0.10.
 */
public final class ArtifactRule implements Rule {

    static final String ID = "artifact";

    record Change(String problem, String fix) {
    }

    private static final Map<String, Change> CHANGES = Map.ofEntries(
            Map.entry("org.springframework.boot:spring-boot-starter-aop", new Change(
                    "spring-boot-starter-aop was renamed to spring-boot-starter-aspectj in Spring Boot 4, "
                            + "so the old name no longer resolves.",
                    "Use org.springframework.boot:spring-boot-starter-aspectj instead.")),
            Map.entry("org.springframework.boot:spring-boot-starter-undertow", new Change(
                    "Spring Boot 4 dropped Undertow, so this starter no longer exists.",
                    "Remove it and use Tomcat (the default in the web starters) or spring-boot-starter-jetty.")),
            Map.entry("org.springframework.boot:spring-boot-starter-pulsar-reactive", new Change(
                    "Spring Boot 4 dropped its reactive Pulsar support, so this starter no longer exists.",
                    "Use spring-boot-starter-pulsar, or manage the reactive Pulsar client yourself.")),
            // In Hibernate 7 this artifact is only a relocation pom, and the Boot 4 BOM manages the new name instead.
            Map.entry("org.hibernate.orm:hibernate-jpamodelgen", new Change(
                    "Hibernate renamed hibernate-jpamodelgen to hibernate-processor, and Spring Boot 4 only manages the new name. "
                            + "Without a version the old one no longer resolves.",
                    "Use org.hibernate.orm:hibernate-processor instead (also in annotationProcessorPaths).")),
            // Still there in 4.x, but deprecated: their 4.0.10 poms name the replacement.
            deprecated("spring-boot-starter-web", "spring-boot-starter-webmvc"),
            deprecated("spring-boot-starter-web-services", "spring-boot-starter-webservices"),
            deprecated("spring-boot-starter-oauth2-client", "spring-boot-starter-security-oauth2-client"),
            deprecated("spring-boot-starter-oauth2-resource-server", "spring-boot-starter-security-oauth2-resource-server"),
            deprecated("spring-boot-starter-oauth2-authorization-server", "spring-boot-starter-security-oauth2-authorization-server"));

    private static Map.Entry<String, Change> deprecated(String starter, String replacement) {
        return Map.entry("org.springframework.boot:" + starter, new Change(
                starter + " is deprecated in Spring Boot 4. It still works for now, but will be removed in a later version.",
                "Use org.springframework.boot:" + replacement + " instead."));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path buildFile : project.buildFiles()) {
            for (Dependency d : Dependency.read(buildFile)) {
                Change change = CHANGES.get(d.coordinates());
                if (change != null) {
                    findings.add(new Finding(ID, project.relativize(buildFile), d.line(), change.problem(), change.fix()));
                }
            }
        }
        return findings;
    }
}
