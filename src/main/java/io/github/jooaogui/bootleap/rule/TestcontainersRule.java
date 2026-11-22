package io.github.jooaogui.bootleap.rule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.jooaogui.bootleap.scan.Dependency;
import io.github.jooaogui.bootleap.scan.Import;
import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * Spring Boot 4 manages Testcontainers 2, which renamed almost every module to {@code testcontainers-<name>}.
 * The old names do not exist in 2.x (there is no relocation), so the build cannot resolve them.
 */
public final class TestcontainersRule implements Rule {

    static final String ID = "testcontainers-2";

    private static final String GROUP = "org.testcontainers";

    private static final Set<String> JUNIT4_RULES = Set.of("org.junit.Rule", "org.junit.ClassRule");

    private final Set<String> renamedModules = load("testcontainers-renamed-modules.txt");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path buildFile : project.buildFiles()) {
            for (Dependency d : Dependency.read(buildFile)) {
                if (!d.group().equals(GROUP)) {
                    continue;
                }
                if (renamedModules.contains(d.artifact())) {
                    findings.add(new Finding(ID, project.relativize(buildFile), d.line(),
                            "Testcontainers 2 (managed by Spring Boot 4) renamed " + d.artifact() + " to testcontainers-"
                                    + d.artifact() + ", and the old name does not exist in 2.x.",
                            "Use " + GROUP + ":testcontainers-" + d.artifact() + " instead."));
                } else if (d.artifact().equals("dynalite")) {
                    findings.add(new Finding(ID, project.relativize(buildFile), d.line(),
                            "Testcontainers 2 removed the dynalite module.",
                            "Use testcontainers-localstack, which can run DynamoDB, or keep Dynalite in a GenericContainer."));
                }
            }
        }
        for (Path source : project.sources()) {
            junit4Rule(project, source).ifPresent(findings::add);
        }
        return findings;
    }

    /**
     * In 1.x every container was a JUnit 4 rule (GenericContainer extended FailureDetectingExternalResource)
     * and the core jar pulled in junit:junit. Both are gone in 2.x.
     */
    private static Optional<Finding> junit4Rule(ScannedProject project, Path source) {
        List<Import> imports = Import.read(source);
        boolean usesTestcontainers = imports.stream().anyMatch(i -> i.isIn("org.testcontainers"));
        return imports.stream()
                .filter(i -> usesTestcontainers && JUNIT4_RULES.contains(i.name()))
                .findFirst()
                .map(i -> new Finding(ID, project.relativize(source), i.line(),
                        "Testcontainers 2 dropped JUnit 4 support: containers are no longer @Rule/@ClassRule objects "
                                + "and junit:junit no longer comes with it.",
                        "Move the test to JUnit 5 with @Testcontainers and @Container (testcontainers-junit-jupiter), "
                                + "or call start() and stop() on the container yourself."));
    }

    private static Set<String> load(String resource) {
        try (InputStream in = TestcontainersRule.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + resource);
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
