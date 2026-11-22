package io.github.jooaogui.bootleap.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dependency declared in a build file. Versions are left out on purpose: the rules care about
 * which artifact is used, and most Spring Boot projects let the BOM pick the version.
 *
 * @param file the build file
 * @param line 1-based line of the artifact name
 * @param group the group id, e.g. {@code org.springframework.boot}
 * @param artifact the artifact id, e.g. {@code spring-boot-starter-web}
 */
public record Dependency(Path file, int line, String group, String artifact) {

    // Maven: <dependency> blocks, and <path> blocks of annotationProcessorPaths.
    private static final Pattern MAVEN_BLOCK = Pattern.compile("<(dependency|path)>(.*?)</\\1>", Pattern.DOTALL);
    private static final Pattern MAVEN_EXCLUSIONS = Pattern.compile("<exclusions>.*?</exclusions>", Pattern.DOTALL);
    private static final Pattern MAVEN_GROUP = Pattern.compile("<groupId>\\s*([\\w.-]+)\\s*</groupId>");
    private static final Pattern MAVEN_ARTIFACT = Pattern.compile("<artifactId>\\s*([\\w.-]+)\\s*</artifactId>");

    // Gradle and version catalogs: "group:artifact" or "group:artifact:version".
    private static final Pattern COORDINATES = Pattern.compile("[\"']([\\w.-]+):([\\w.-]+)(?::[^\"']*)?[\"']");
    // Version catalogs can also say: group = "g", name = "a".
    private static final Pattern CATALOG_ENTRY = Pattern.compile("group\\s*=\\s*\"([\\w.-]+)\"\\s*,\\s*name\\s*=\\s*\"([\\w.-]+)\"");

    /** The {@code group:artifact} pair. */
    public String coordinates() {
        return group + ":" + artifact;
    }

    /** Reads the dependencies of a pom.xml, Gradle build file or version catalog. Other files give none. */
    public static List<Dependency> read(Path buildFile) {
        String name = buildFile.getFileName().toString();
        if (name.equals("pom.xml")) {
            return readMaven(buildFile);
        }
        if (name.endsWith(".gradle") || name.endsWith(".gradle.kts") || name.endsWith(".toml")) {
            return readGradle(buildFile);
        }
        return List.of();
    }

    private static List<Dependency> readMaven(Path pom) {
        String xml = String.join("\n", TextFiles.lines(pom));
        List<Dependency> dependencies = new ArrayList<>();
        Matcher block = MAVEN_BLOCK.matcher(xml);
        while (block.find()) {
            // Blank out exclusions instead of removing them, so offsets (and line numbers) stay right.
            String body = MAVEN_EXCLUSIONS.matcher(block.group(2)).replaceAll(m -> " ".repeat(m.group().length()));
            Matcher group = MAVEN_GROUP.matcher(body);
            Matcher artifact = MAVEN_ARTIFACT.matcher(body);
            if (group.find() && artifact.find()) {
                int offset = block.start(2) + artifact.start();
                dependencies.add(new Dependency(pom, TextFiles.lineAt(xml, offset), group.group(1), artifact.group(1)));
            }
        }
        return dependencies;
    }

    private static List<Dependency> readGradle(Path file) {
        List<String> lines = TextFiles.lines(file);
        List<Dependency> dependencies = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith("//") || line.trim().startsWith("#")) {
                continue;
            }
            for (Pattern pattern : List.of(COORDINATES, CATALOG_ENTRY)) {
                Matcher m = pattern.matcher(line);
                while (m.find()) {
                    dependencies.add(new Dependency(file, i + 1, m.group(1), m.group(2)));
                }
            }
        }
        return dependencies;
    }
}
