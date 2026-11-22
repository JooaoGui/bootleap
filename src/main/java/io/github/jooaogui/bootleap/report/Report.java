package io.github.jooaogui.bootleap.report;

import java.io.PrintStream;
import java.util.List;

import io.github.jooaogui.bootleap.rule.Finding;

/** Writes the findings in one output format. */
public interface Report {

    /**
     * @param context one sentence about the run, e.g. which Spring Boot version was checked against
     * @param findings what the rules found, in rule order
     * @param out where to write
     */
    void print(String context, List<Finding> findings, PrintStream out);
}
