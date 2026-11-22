package io.github.jooaogui.bootleap.rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.jooaogui.bootleap.scan.Dependency;
import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * In Spring Boot 4 the Flyway and Liquibase auto-configuration moved to their own modules
 * (spring-boot-flyway, spring-boot-liquibase). Having only the library is no longer enough:
 * the build passes, and the migrations quietly stop running at startup.
 *
 * <p>Checked against the 4.0.10 poms: only the matching starter or module brings the auto-configuration,
 * not spring-boot-starter-jdbc or spring-boot-starter-data-jpa.
 */
public final class MigrationStarterRule implements Rule {

    static final String ID = "migration-starter";

    record Tool(String name, String library, String starter, Set<String> enablers) {
    }

    private static final List<Tool> TOOLS = List.of(
            new Tool("Flyway", "org.flywaydb:flyway-core", "spring-boot-starter-flyway", Set.of(
                    "org.springframework.boot:spring-boot-starter-flyway", "org.springframework.boot:spring-boot-flyway")),
            new Tool("Liquibase", "org.liquibase:liquibase-core", "spring-boot-starter-liquibase", Set.of(
                    "org.springframework.boot:spring-boot-starter-liquibase", "org.springframework.boot:spring-boot-liquibase")));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Dependency> dependencies = project.buildFiles().stream()
                .flatMap(file -> Dependency.read(file).stream())
                .toList();
        List<Finding> findings = new ArrayList<>();
        for (Tool tool : TOOLS) {
            // Look at the whole repository: in a multi-module build the starter may sit in another module.
            boolean enabled = dependencies.stream().anyMatch(d -> tool.enablers().contains(d.coordinates()));
            if (enabled) {
                continue;
            }
            dependencies.stream()
                    .filter(d -> d.coordinates().equals(tool.library()))
                    .forEach(d -> findings.add(finding(project, d.file(), d.line(), tool)));
        }
        return findings;
    }

    private static Finding finding(ScannedProject project, Path file, int line, Tool tool) {
        String library = tool.library().substring(tool.library().indexOf(':') + 1);
        return new Finding(
                ID,
                project.relativize(file),
                line,
                library + " is used without " + tool.starter() + ". In Spring Boot 4 the " + tool.name()
                        + " auto-configuration lives in its own module, so the build still passes but the migrations "
                        + "stop running at startup.",
                "Add org.springframework.boot:" + tool.starter() + " (it brings " + library + " with it).");
    }
}
