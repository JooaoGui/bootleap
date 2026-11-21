package io.github.jooaogui.bootleap.report;

import java.io.PrintStream;
import java.util.List;

import io.github.jooaogui.bootleap.rule.Finding;

/**
 * Plain text report for the terminal.
 */
public final class TextReport {

    public void print(List<Finding> findings, PrintStream out) {
        if (findings.isEmpty()) {
            out.println("No known Spring Boot 4 problems found.");
            return;
        }
        out.println(findings.size() + " thing(s) to fix before Spring Boot 4:");
        out.println();
        String previousFix = null;
        for (Finding f : findings) {
            out.println("[" + f.ruleId() + "] " + f.location());
            out.println("  " + f.problem());
            // A rule that hits many files gives the same fix each time; print it once.
            out.println("  Fix: " + (f.fix().equals(previousFix) ? "same as above." : f.fix()));
            out.println();
            previousFix = f.fix();
        }
    }
}
