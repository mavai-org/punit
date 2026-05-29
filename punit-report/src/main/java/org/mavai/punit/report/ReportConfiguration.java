package org.mavai.punit.report;

import java.nio.file.Path;

/**
 * Configuration for PUnit XML report generation.
 *
 * <p>Resolution order: system property → environment variable → default.
 */
public final class ReportConfiguration {

    private static final String DIR_PROPERTY = "punit.report.dir";
    private static final String DIR_ENV_VAR = "PUNIT_REPORT_DIR";
    private static final String ENABLED_PROPERTY = "punit.report.enabled";
    private static final String DEFAULT_DIR = "build/reports/punit/xml";

    private final Path outputDirectory;
    private final boolean enabled;

    private ReportConfiguration(Path outputDirectory, boolean enabled) {
        this.outputDirectory = outputDirectory;
        this.enabled = enabled;
    }

    /**
     * Resolves configuration from system properties and environment variables.
     */
    public static ReportConfiguration resolve() {
        String enabledValue = System.getProperty(ENABLED_PROPERTY);
        boolean enabled = enabledValue == null || Boolean.parseBoolean(enabledValue);

        String dir = System.getProperty(DIR_PROPERTY);
        if (dir == null || dir.isBlank()) {
            dir = System.getenv(DIR_ENV_VAR);
        }
        if (dir == null || dir.isBlank()) {
            dir = DEFAULT_DIR;
        }

        return new ReportConfiguration(Path.of(dir), enabled);
    }

    /**
     * Creates a configuration with explicit values (for testing).
     */
    public static ReportConfiguration of(Path outputDirectory, boolean enabled) {
        return new ReportConfiguration(outputDirectory, enabled);
    }

    public Path outputDirectory() {
        return outputDirectory;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Computes the XML file path for a given test identity.
     *
     * @param className  fully qualified class name
     * @param methodName test method name
     * @return the path to the XML file
     */
    public Path xmlFilePath(String className, String methodName) {
        String filename = className + "." + methodName + ".xml";
        return outputDirectory.resolve(filename);
    }
}
