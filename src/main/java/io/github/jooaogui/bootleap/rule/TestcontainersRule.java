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
import java.util.Set;
import java.util.stream.Collectors;

import io.github.jooaogui.bootleap.scan.Dependency;
import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * Spring Boot 4 manages Testcontainers 2, which renamed almost every module to {@code testcontainers-<name>}.
 * The old names do not exist in 2.x (there is no relocation), so the build cannot resolve them.
 */
public final class TestcontainersRule implements Rule {

    static final String ID = "testcontainers-2";

    private static final String GROUP = "org.testcontainers";

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
        return findings;
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
