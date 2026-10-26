package org.bukkit.support.suite;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Test suite for Kindling's own code, which needs no registries")
@SelectPackages("net.kindlingmc")
public class KindlingTestSuite {
}
