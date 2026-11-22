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

    // Gradle: id 'org.springframework.boot' version '4.0.1' / id("org.springframework.boot") version "4.0.1",
    // where the version can also be "${springBootVersion}" or a bare springBootVersion from gradle.properties.
    private static final Pattern GRADLE_PLUGIN = Pattern.compile(
            "org\\.springframework\\.boot[\"']\\s*\\)?\\s*version\\s*\\(?\\s*([\"'][^\"']+[\"']|[\\w.]+)");
    // platform("org.springframework.boot:spring-boot-dependencies:4.0.1"), also inside version catalogs.
    private static final Pattern GRADLE_BOM = Pattern.compile("org\\.springframework\\.boot:spring-boot-dependencies:([^\"'\\s)]+)");
    private static final Pattern GRADLE_REFERENCE = Pattern.compile("\\$?\\{?([\\w.]+)}?");
    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*([\\w.-]+)\\s*[=:]\\s*[\"']?([^\"'\\s]+)[\"']?\\s*$");

    // Version catalogs: { id = "org.springframework.boot", version.ref = "spring-boot" } or version = "4.0.1".
    private static final Pattern CATALOG_REF = Pattern.compile("version\\.ref\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern CATALOG_VERSION = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"");

    /** A {@code key = value} found in gradle.properties or in the [versions] of a version catalog. */
    record Property(String value, Path file, int line) {
    }

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
        Map<String, Property> gradleProperties = new HashMap<>();
        project.buildFiles().stream()
                .filter(file -> file.getFileName().toString().equals("gradle.properties"))
                .forEach(file -> gradleProperties.putAll(keyValues(file, TextFiles.lines(file))));
        return project.buildFiles().stream()
                .flatMap(file -> read(file, gradleProperties).stream())
                .max(Comparator.comparingInt(BootVersion::major).thenComparingInt(BootVersion::minor));
    }

    static List<BootVersion> read(Path file, Map<String, Property> gradleProperties) {
        String name = file.getFileName().toString();
        if (name.equals("pom.xml")) {
            return readMaven(file);
        }
        if (name.endsWith(".gradle") || name.endsWith(".gradle.kts")) {
            return readGradle(file, gradleProperties);
        }
        if (name.endsWith(".toml")) {
            return readCatalog(file);
        }
        return List.of();
    }

    private static List<BootVersion> readGradle(Path file, Map<String, Property> gradleProperties) {
        List<String> lines = TextFiles.lines(file);
        List<BootVersion> versions = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("//")) {
                continue;
            }
            for (Pattern pattern : List.of(GRADLE_PLUGIN, GRADLE_BOM)) {
                Matcher m = pattern.matcher(lines.get(i));
                if (!m.find()) {
                    continue;
                }
                String token = m.group(1).replace("\"", "").replace("'", "");
                Matcher reference = GRADLE_REFERENCE.matcher(token);
                if (!NUMBER.matcher(token).matches() && reference.matches() && gradleProperties.containsKey(reference.group(1))) {
                    Property p = gradleProperties.get(reference.group(1));
                    add(versions, p.value(), p.file(), p.line());
                } else {
                    add(versions, token, file, i + 1);
                }
            }
        }
        return versions;
    }

    private static List<BootVersion> readCatalog(Path file) {
        List<String> lines = TextFiles.lines(file);
        Map<String, Property> catalogVersions = keyValues(file, sectionLines(lines, "versions"));
        List<BootVersion> versions = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher bom = GRADLE_BOM.matcher(line);
            if (bom.find()) {
                add(versions, bom.group(1), file, i + 1);
                continue;
            }
            if (!line.contains("\"org.springframework.boot\"") && !line.contains("\"org.springframework.boot:")) {
                continue;
            }
            Matcher ref = CATALOG_REF.matcher(line);
            Matcher literal = CATALOG_VERSION.matcher(line);
            if (ref.find() && catalogVersions.containsKey(ref.group(1))) {
                Property p = catalogVersions.get(ref.group(1));
                add(versions, p.value(), file, p.line());
            } else if (literal.find()) {
                add(versions, literal.group(1), file, i + 1);
            }
        }
        return versions;
    }

    /** The lines of one [section] of a TOML file; lines of other sections are blanked so line numbers stay right. */
    private static List<String> sectionLines(List<String> lines, String section) {
        List<String> result = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.trim().startsWith("[")) {
                inside = line.trim().equals("[" + section + "]");
            }
            result.add(inside ? line : "");
        }
        return result;
    }

    private static Map<String, Property> keyValues(Path file, List<String> lines) {
        Map<String, Property> values = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = KEY_VALUE.matcher(lines.get(i));
            if (m.matches()) {
                values.put(m.group(1), new Property(m.group(2), file, i + 1));
            }
        }
        return values;
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
