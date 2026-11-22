package io.github.jooaogui.bootleap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jooaogui.bootleap.report.MarkdownReport;
import io.github.jooaogui.bootleap.report.Report;
import io.github.jooaogui.bootleap.report.TextReport;
import io.github.jooaogui.bootleap.rule.ArtifactRule;
import io.github.jooaogui.bootleap.rule.Finding;
import io.github.jooaogui.bootleap.rule.GradleVersionRule;
import io.github.jooaogui.bootleap.rule.Jackson3Rule;
import io.github.jooaogui.bootleap.rule.MigrationStarterRule;
import io.github.jooaogui.bootleap.rule.MockBeanRule;
import io.github.jooaogui.bootleap.rule.MovedClassRule;
import io.github.jooaogui.bootleap.rule.Rule;
import io.github.jooaogui.bootleap.rule.TestcontainersRule;
import io.github.jooaogui.bootleap.scan.BootVersion;
import io.github.jooaogui.bootleap.scan.ProjectScanner;
import io.github.jooaogui.bootleap.scan.ScannedProject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command line entry point.
 *
 * <p>Exit codes: 0 = nothing found (or {@code --report-only}), 1 = findings, 2 = bad input.
 */
@Command(
        name = "bootleap",
        mixinStandardHelpOptions = true,
        versionProvider = BootleapCommand.ManifestVersion.class,
        description = "Lists what is likely to break when upgrading a project to Spring Boot 4.")
public class BootleapCommand implements Callable<Integer> {

    private static final Pattern MINOR_VERSION = Pattern.compile("(\\d+\\.\\d+)(\\.\\d+)?");

    @Parameters(index = "0", defaultValue = ".", description = "Project directory (default: current directory).")
    Path projectDir;

    @Option(names = "--to", paramLabel = "VERSION",
            description = "Spring Boot version you are moving to: 4.0, 4.1 or 4.2. "
                    + "Default: the 4.x version in your build files, or the newest one.")
    String target;

    @Option(names = "--report-only", description = "Always exit with code 0, even when something is found.")
    boolean reportOnly;

    /** Output formats. The names are what users type after --format. */
    enum Format {
        text(new TextReport()),
        markdown(new MarkdownReport());

        final Report report;

        Format(Report report) {
            this.report = report;
        }
    }

    @Option(names = "--format", paramLabel = "FORMAT", defaultValue = "text",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). "
                    + "Markdown is made for pull request comments.")
    Format format;

    @Option(names = "--only-if-upgrading",
            description = "Only check when the build files already ask for Spring Boot 4, as on a Dependabot branch. "
                    + "Otherwise print nothing and exit with 0. Ignored with --to.")
    boolean onlyIfUpgrading;

    static List<Rule> rules(String target) {
        return List.of(
                new GradleVersionRule(),
                new ArtifactRule(),
                new MigrationStarterRule(),
                new TestcontainersRule(),
                new Jackson3Rule(),
                new MockBeanRule(),
                new MovedClassRule(target));
    }

    @Override
    public Integer call() {
        if (!Files.isDirectory(projectDir)) {
            System.err.println("Not a directory: " + projectDir);
            return 2;
        }
        Choice choice;
        ScannedProject project = new ProjectScanner().scan(projectDir.toAbsolutePath().normalize());
        if (target != null) {
            Matcher version = MINOR_VERSION.matcher(target);
            if (!version.matches() || !MovedClassRule.TARGETS.contains(version.group(1))) {
                System.err.println("Unknown Spring Boot version: " + target + ". Use one of " + MovedClassRule.TARGETS + ".");
                return 2;
            }
            choice = new Choice(version.group(1), "Checking against Spring Boot " + version.group(1) + ".");
        } else {
            Optional<BootVersion> found = BootVersion.detect(project);
            if (onlyIfUpgrading && found.map(v -> v.major() < 4).orElse(true)) {
                // Nothing on stdout, so a script (like the GitHub Action) knows there is no report.
                System.err.println("The build does not ask for Spring Boot 4 yet, so there is nothing to check. "
                        + "Run without --only-if-upgrading to check anyway.");
                return 0;
            }
            choice = choose(project, found);
        }
        List<Finding> findings = rules(choice.version()).stream()
                .flatMap(rule -> rule.check(project).stream())
                .toList();
        format.report.print(choice.message(), findings, System.out);
        return findings.isEmpty() || reportOnly ? 0 : 1;
    }

    /** Reads the version that the build writes into the jar manifest, so it lives only in pom.xml. */
    static final class ManifestVersion implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = BootleapCommand.class.getPackage().getImplementationVersion();
            return new String[] {"bootleap " + (version != null ? version : "(development build)")};
        }
    }

    /** The Spring Boot version to check against, and a line telling the user why. */
    record Choice(String version, String message) {
    }

    /**
     * Without --to, use the version in the build files. On a Dependabot branch it is already 4.x;
     * on a project still on 3.x, check against the newest 4.x.
     */
    static Choice choose(ScannedProject project, Optional<BootVersion> found) {
        List<String> targets = MovedClassRule.TARGETS;
        String newest = targets.get(targets.size() - 1);
        if (found.isEmpty()) {
            return new Choice(newest, "No Spring Boot version found in the build files, checking against Spring Boot "
                    + newest + ". Use --to to pick another one.");
        }
        BootVersion v = found.get();
        String where = v.version() + " in " + project.relativize(v.file()).toString().replace('\\', '/') + ":" + v.line();
        String minor = v.major() + "." + v.minor();
        if (targets.contains(minor)) {
            return new Choice(minor, "Checking against Spring Boot " + minor + " (found " + where + ").");
        }
        if (v.major() >= 4) {
            return new Choice(newest, "Found Spring Boot " + where + ", which bootleap does not know yet, "
                    + "checking against Spring Boot " + newest + ".");
        }
        return new Choice(newest, "The build is on Spring Boot " + where + ", checking against Spring Boot " + newest
                + ". Use --to to pick another 4.x version.");
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new BootleapCommand()).execute(args));
    }
}
