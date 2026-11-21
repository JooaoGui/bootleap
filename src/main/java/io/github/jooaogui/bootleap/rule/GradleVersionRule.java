package io.github.jooaogui.bootleap.rule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * The Spring Boot 4 Gradle plugin needs Gradle 8.14 or later (or 9.x).
 * With an older wrapper the build stops before compiling anything, which hides every other problem.
 */
public final class GradleVersionRule implements Rule {

    static final String ID = "gradle-version";

    private static final Pattern DISTRIBUTION = Pattern.compile("gradle-(\\d+)\\.(\\d+)(?:\\.\\d+)?(?:-[\\w.-]+?)?-(?:bin|all)\\.zip");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        // A repository can hold several Gradle builds, each with its own wrapper.
        return project.buildFiles().stream()
                .filter(p -> p.getFileName().toString().equals("gradle-wrapper.properties"))
                .map(file -> check(project, file))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Finding> check(ScannedProject project, Path file) {
        List<String> lines = read(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.trim().startsWith("distributionUrl")) {
                continue;
            }
            Matcher m = DISTRIBUTION.matcher(line);
            if (!m.find()) {
                return Optional.empty();
            }
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            if (major > 8 || (major == 8 && minor >= 14)) {
                return Optional.empty();
            }
            return Optional.of(new Finding(
                    ID,
                    project.relativize(file),
                    i + 1,
                    "Gradle " + major + "." + minor + " is too old. The Spring Boot 4 plugin needs Gradle 8.14 or later, "
                            + "so the build fails before compiling anything.",
                    "Run ./gradlew wrapper --gradle-version latest (anything from 8.14 up works) and commit the wrapper files."));
        }
        return Optional.empty();
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
