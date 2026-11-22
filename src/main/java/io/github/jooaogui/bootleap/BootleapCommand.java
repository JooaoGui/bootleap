package io.github.jooaogui.bootleap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import io.github.jooaogui.bootleap.report.TextReport;
import io.github.jooaogui.bootleap.rule.Finding;
import io.github.jooaogui.bootleap.rule.GradleVersionRule;
import io.github.jooaogui.bootleap.rule.Jackson3Rule;
import io.github.jooaogui.bootleap.rule.MockBeanRule;
import io.github.jooaogui.bootleap.rule.MovedClassRule;
import io.github.jooaogui.bootleap.rule.Rule;
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
        version = "bootleap 0.1.0-SNAPSHOT",
        description = "Lists what is likely to break when upgrading a project to Spring Boot 4.")
public class BootleapCommand implements Callable<Integer> {

    static final List<Rule> RULES = List.of(
            new GradleVersionRule(),
            new Jackson3Rule(),
            new MockBeanRule(),
            new MovedClassRule());

    @Parameters(index = "0", defaultValue = ".", description = "Project directory (default: current directory).")
    Path projectDir;

    @Option(names = "--report-only", description = "Always exit with code 0, even when something is found.")
    boolean reportOnly;

    @Override
    public Integer call() {
        if (!Files.isDirectory(projectDir)) {
            System.err.println("Not a directory: " + projectDir);
            return 2;
        }
        ScannedProject project = new ProjectScanner().scan(projectDir.toAbsolutePath().normalize());
        List<Finding> findings = RULES.stream()
                .flatMap(rule -> rule.check(project).stream())
                .toList();
        new TextReport().print(findings, System.out);
        return findings.isEmpty() || reportOnly ? 0 : 1;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new BootleapCommand()).execute(args));
    }
}
