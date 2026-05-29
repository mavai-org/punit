package org.mavai.punit.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.VerdictSink;

/**
 * A {@link VerdictSink} that writes each verdict as an XML file.
 *
 * <p>Each verdict produces one file at:
 * {@code {outputDir}/{className}.{methodName}.xml}
 *
 * <p>Discovered as a {@link VerdictSink} {@code ServiceLoader} entry
 * (see {@code META-INF/services/org.mavai.punit.verdict.VerdictSink})
 * so PUnit's runtime auto-installs an instance whenever
 * {@code punit-report} is on the classpath. Authors can construct
 * an instance directly with explicit configuration; the no-arg
 * constructor used by {@code ServiceLoader} resolves configuration
 * via {@link ReportConfiguration#resolve()}.
 */
public final class VerdictXmlSink implements VerdictSink {

    private static final Logger logger = LogManager.getLogger(VerdictXmlSink.class);

    private final VerdictXmlWriter writer = new VerdictXmlWriter();
    private final ReportConfiguration config;

    /** Used by {@code ServiceLoader}; resolves config from system properties + env. */
    public VerdictXmlSink() {
        this(ReportConfiguration.resolve());
    }

    public VerdictXmlSink(ReportConfiguration config) {
        this.config = config;
    }

    @Override
    public void accept(ProbabilisticTestVerdict verdict) {
        if (!config.enabled()) {
            return;
        }

        Path xmlFile = config.xmlFilePath(
                verdict.identity().className(),
                verdict.identity().methodName());

        try {
            Files.createDirectories(xmlFile.getParent());
            try (OutputStream out = Files.newOutputStream(xmlFile)) {
                writer.write(verdict, out);
            }
            logger.debug("Wrote verdict XML: {}", xmlFile);
        } catch (IOException | XMLStreamException e) {
            logger.warn("Failed to write verdict XML to {}: {}", xmlFile, e.getMessage());
        }
    }
}
