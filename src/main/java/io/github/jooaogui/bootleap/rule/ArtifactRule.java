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

    private static final Map<String, Change> CHANGES = Map.of(
            "org.springframework.boot:spring-boot-starter-aop", new Change(
                    "spring-boot-starter-aop was renamed to spring-boot-starter-aspectj in Spring Boot 4, "
                            + "so the old name no longer resolves.",
                    "Use org.springframework.boot:spring-boot-starter-aspectj instead."),
            "org.springframework.boot:spring-boot-starter-undertow", new Change(
                    "Spring Boot 4 dropped Undertow, so this starter no longer exists.",
                    "Remove it and use Tomcat (the default in the web starters) or spring-boot-starter-jetty."),
            "org.springframework.boot:spring-boot-starter-pulsar-reactive", new Change(
                    "Spring Boot 4 dropped its reactive Pulsar support, so this starter no longer exists.",
                    "Use spring-boot-starter-pulsar, or manage the reactive Pulsar client yourself."));

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
