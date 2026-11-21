package io.github.jooaogui.bootleap.rule;

import java.util.List;

import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * A single check. Rules only read files; they never change the project.
 */
public interface Rule {

    String id();

    List<Finding> check(ScannedProject project);
}
