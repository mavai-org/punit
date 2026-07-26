package org.mavai.punit.decl.internal.run;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.parser.ContractParser;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Resolves a contract by its declared {@code contract:} name — never by
 * file path — against the {@code *.yaml} contract files in the test
 * class's resource package (resources mirror the test package). The
 * common case has zero strings: the calling method's name kebab-cases
 * into the contract name, and renaming a file is free.
 */
public final class ContractLocator {

    private ContractLocator() {}

    record Located(ContractDeclaration declaration, String resourceName) {}

    static Located locate(Class<?> caller, String methodName, String explicitName) {
        String wanted = explicitName != null ? explicitName : kebabCase(methodName);
        String packagePath = caller.getPackageName().replace('.', '/');
        List<Candidate> candidates = candidates(caller, packagePath);
        List<Candidate> matches = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String declared = probeContractName(candidate.text());
            if (declared == null) {
                continue;
            }
            seen.add(candidate.name() + " [" + declared + "]");
            if (declared.equals(wanted)) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            String looked = seen.isEmpty() ? "no contract files found" : "looked at: " + String.join(", ", seen);
            throw new ContractConfigurationException(
                    "no contract file in package '" + caller.getPackageName() + "' declares "
                            + "contract: '" + wanted + "' (" + looked + "). The name resolves "
                            + "from the test method '" + methodName + "'; PUnit.declared(\"<contract-name>\") overrides");
        }
        if (matches.size() > 1) {
            List<String> names = matches.stream().map(Candidate::name).toList();
            throw new ContractConfigurationException(
                    "contract '" + wanted + "' is declared by more than one file in package '"
                            + caller.getPackageName() + "': " + String.join(", ", names)
                            + " — a contract name identifies exactly one file");
        }
        Candidate match = matches.get(0);
        return new Located(ContractParser.parse(match.text(), match.path()), match.name());
    }

    /** The calling method's name as a contract name: kebab-cased. */
    static String kebabCase(String methodName) {
        String unwrapped = unwrapLambda(methodName);
        StringBuilder kebab = new StringBuilder(unwrapped.length() + 8);
        for (int i = 0; i < unwrapped.length(); i++) {
            char c = unwrapped.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                kebab.append('-');
            }
            if (c == '_') {
                kebab.append('-');
            } else {
                kebab.append(Character.toLowerCase(c));
            }
        }
        return kebab.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Recovers the enclosing method's name from a synthetic lambda
     * frame ({@code lambda$method$0}) — an author wrapping the call in
     * a lambda still resolves to their test method.
     */
    private static String unwrapLambda(String methodName) {
        if (!methodName.startsWith("lambda$")) {
            return methodName;
        }
        int end = methodName.lastIndexOf('$');
        return end > "lambda$".length() ? methodName.substring("lambda$".length(), end) : methodName;
    }

    private record Candidate(String name, String text, Path path) {}

    private static List<Candidate> candidates(Class<?> caller, String packagePath) {
        List<Candidate> candidates = new ArrayList<>();
        ClassLoader loader = caller.getClassLoader();
        try {
            Enumeration<URL> roots = loader.getResources(packagePath);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if ("file".equals(root.getProtocol())) {
                    Path directory = Paths.get(root.toURI());
                    try (var entries = Files.list(directory)) {
                        for (Path file : entries.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                            candidates.add(new Candidate(
                                    file.getFileName().toString(),
                                    Files.readString(file, StandardCharsets.UTF_8),
                                    file));
                        }
                    }
                } else if ("jar".equals(root.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) root.openConnection();
                    try (JarFile jar = new JarFile(connection.getJarFileURL().toURI().getPath())) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith(packagePath + "/")
                                    && name.endsWith(".yaml")
                                    && name.indexOf('/', packagePath.length() + 1) < 0) {
                                try (InputStream in = jar.getInputStream(entry)) {
                                    candidates.add(new Candidate(
                                            name.substring(packagePath.length() + 1),
                                            new String(in.readAllBytes(), StandardCharsets.UTF_8),
                                            null));
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (URISyntaxException error) {
            throw new ContractConfigurationException(
                    "cannot scan resource package '" + packagePath + "': " + error.getMessage(), error);
        }
        return candidates;
    }

    /**
     * A cheap probe: the file's declared {@code contract:} name when it
     * is a contract file ({@code format: mavai-contract/1}), else
     * {@code null}. Full validation happens only on the matched file.
     */
    private static String probeContractName(String text) {
        Object data;
        try {
            data = new Load(LoadSettings.builder().build()).loadFromString(text);
        } catch (RuntimeException notYaml) {
            return null;
        }
        if (!(data instanceof Map<?, ?> mapping)) {
            return null;
        }
        if (!ContractDeclaration.FORMAT_IDENTIFIER.equals(mapping.get("format"))) {
            return null;
        }
        Object name = mapping.get("contract");
        return name instanceof String value && !value.isEmpty() ? value : null;
    }
}
