package io.github.jooaogui.bootleap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Command line entry point.
 */
@Command(
        name = "bootleap",
        mixinStandardHelpOptions = true,
        version = "bootleap 0.1.0-SNAPSHOT",
        description = "Lists what is likely to break when upgrading a project to Spring Boot 4.")
public class BootleapCommand implements Callable<Integer> {

    @Parameters(index = "0", defaultValue = ".", description = "Project directory (default: current directory).")
    Path projectDir;

    @Override
    public Integer call() {
        if (!Files.isDirectory(projectDir)) {
            System.err.println("Not a directory: " + projectDir);
            return 2;
        }
        System.out.println("Checking " + projectDir.toAbsolutePath().normalize());
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new BootleapCommand()).execute(args));
    }
}
