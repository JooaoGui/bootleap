package io.github.jooaogui.bootleap.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Spring Boot version a build file asks for. On a Dependabot branch this is already the new
 * version, which is exactly what bootleap should check against.
 *
 * @param version the version as written, e.g. {@code 4.0.1}
 * @param file the build file it was found in
 * @param line 1-based line of the version
 */
public record BootVersion(String version, Path file, int line) {

    private static final Pattern NUMBER = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.\\d+)?(?:[-.][\\w.-]+)?");

    // Maven
    private static final Pattern MAVEN_BLOCK = Pattern.compile("<(parent|dependency)>(.*?)</\\1>", Pattern.DOTALL);
    private static final Pattern MAVEN_BOOT = Pattern.compile(
            "<groupId>\\s*org\\.springframework\\.boot\\s*</groupId>\\s*"
                    + "<artifactId>\\s*(spring-boot-starter-parent|spring-boot-dependencies)\\s*</artifactId>");
    private static final Pattern MAVEN_VERSION = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>");
    private static final Pattern MAVEN_PROPERTIES = Pattern.compile("<properties>(.*?)</properties>", Pattern.DOTALL);
    private static final Pattern MAVEN_PROPERTY = Pattern.compile("<([\\w.-]+)>\\s*([^<\\s]+)\\s*</\\1>");
    private static final Pattern MAVEN_REFERENCE = Pattern.compile("\\$\\{([\\w.-]+)}");

    public int major() {
        return part(1);
    }

    public int minor() {
        return part(2);
    }

    private int part(int group) {
        Matcher m = NUMBER.matcher(version);
        if (!m.matches()) {
            throw new IllegalStateException("Not a version: " + version);
        }
        return Integer.parseInt(m.group(group));
    }

    /** The newest Spring Boot version found in any build file of the project. */
    public static Optional<BootVersion> detect(ScannedProject project) {
        return project.buildFiles().stream()
                .flatMap(file -> read(file).stream())
                .max(Comparator.comparingInt(BootVersion::major).thenComparingInt(BootVersion::minor));
    }

    static List<BootVersion> read(Path file) {
        String name = file.getFileName().toString();
        if (name.equals("pom.xml")) {
            return readMaven(file);
        }
        return List.of();
    }

    private static List<BootVersion> readMaven(Path pom) {
        String xml = String.join("\n", TextFiles.lines(pom));
        Map<String, Integer> propertyOffsets = new HashMap<>();
        Map<String, String> properties = new HashMap<>();
        Matcher section = MAVEN_PROPERTIES.matcher(xml);
        while (section.find()) {
            Matcher p = MAVEN_PROPERTY.matcher(section.group(1));
            while (p.find()) {
                properties.put(p.group(1), p.group(2));
                propertyOffsets.put(p.group(1), section.start(1) + p.start(2));
            }
        }
        List<BootVersion> versions = new ArrayList<>();
        Matcher block = MAVEN_BLOCK.matcher(xml);
        while (block.find()) {
            String body = block.group(2);
            Matcher version = MAVEN_VERSION.matcher(body);
            if (!MAVEN_BOOT.matcher(body).find() || !version.find()) {
                continue;
            }
            String value = version.group(1);
            int offset = block.start(2) + version.start(1);
            Matcher reference = MAVEN_REFERENCE.matcher(value);
            if (reference.matches() && properties.containsKey(reference.group(1))) {
                // Point at the property, which is the line people edit.
                value = properties.get(reference.group(1));
                offset = propertyOffsets.get(reference.group(1));
            }
            add(versions, value, pom, TextFiles.lineAt(xml, offset));
        }
        return versions;
    }

    private static void add(List<BootVersion> versions, String value, Path file, int line) {
        if (NUMBER.matcher(value).matches()) {
            versions.add(new BootVersion(value, file, line));
        }
    }
}
