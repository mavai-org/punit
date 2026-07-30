package org.mavai.punit.decl.internal.parser;

import static org.mavai.punit.decl.internal.parser.Yaml.fail;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Named path anchors — the {@code roots:} block both declarative
 * loaders share. A file declares directory anchors once
 * ({@code roots: {corpus: ../shared}}), and every file-referencing
 * position may reach through one with {@code @<name>/…} instead of
 * encoding the file's own location into a {@code ../../..} hop-chain.
 * Roots are per-file: the contract file's and the services file's
 * roots are independent namespaces — nothing shared, inherited, or
 * discovered upward.
 *
 * <p>The override channel replaces a declared value entirely and may
 * be absolute — the machine-local channel, which keeps committed files
 * portable. Per punit's configuration tier it is the
 * {@code mavai.root.<name>} system property first, then the family's
 * {@code MAVAI_ROOT_<NAME>} environment variable; read once per file
 * load, never per sample.
 *
 * <p>Identity is untouched by design: file-sourced inputs fingerprint
 * by content, never by path — a root changes only the path resolution
 * ahead of the read.
 */
// mavai-ref: JVI-W9D0VWD — do not remove (resolves in mavai-orchestrator)
public final class Roots {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Pattern DRIVE_ABSOLUTE = Pattern.compile("^[A-Za-z]:[/\\\\].*");

    /** name → (resolved directory, declared value, overridden). */
    private final Map<String, Anchor> anchors;
    private final String fileWhat;
    private final Set<String> used = new LinkedHashSet<>();

    private record Anchor(Path resolved, String declared, boolean overridden) {}

    private Roots(Map<String, Anchor> anchors, String fileWhat) {
        this.anchors = anchors;
        this.fileWhat = fileWhat;
    }

    /** No block declared: every reference through {@code @} refuses as undeclared. */
    public static Roots none(String fileWhat) {
        return new Roots(Map.of(), fileWhat);
    }

    /**
     * Validates a {@code roots:} block and resolves each anchor —
     * refusals surface at load, zero samples.
     */
    public static Roots parse(Object block, Path baseDir, String fileWhat) {
        if (block == null) {
            return none(fileWhat);
        }
        if (!(block instanceof Map<?, ?> mapping) || mapping.isEmpty()) {
            throw fail(fileWhat + ": `roots:` must be a non-empty mapping of root names to "
                    + "relative directory paths — omit the block to declare no roots");
        }
        if (baseDir == null) {
            throw fail(fileWhat + ": `roots:` needs a file loaded from disk — the anchors "
                    + "resolve relative to the declaring file's directory");
        }
        Map<String, Anchor> anchors = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!NAME.matcher(name).matches()) {
                throw fail(fileWhat + ": root name '" + name + "' must match "
                        + "[a-z][a-z0-9-]* — lower-case, digits, hyphens");
            }
            if (!(entry.getValue() instanceof String declared) || declared.isEmpty()) {
                throw fail(fileWhat + ": root `" + name + ":` declares no directory — its "
                        + "value is a non-empty relative path");
            }
            if (isAbsolute(declared)) {
                throw fail(fileWhat + ": root `" + name + ":` is absolute (" + declared
                        + ") — a declared root is relative to the declaring file; an "
                        + "absolute location is the " + overrideVariable(name)
                        + " override's job");
            }
            String override = override(name);
            boolean overridden = override != null;
            String effective = overridden ? override : declared;
            Path resolved = (Path.of(effective).isAbsolute()
                    ? Path.of(effective)
                    : baseDir.resolve(effective)).normalize().toAbsolutePath();
            if (!java.nio.file.Files.isDirectory(resolved)) {
                throw fail(fileWhat + ": root `" + name + ":` resolves to " + resolved
                        + ", which is not an existing directory"
                        + (overridden ? " (via " + overrideVariable(name) + ")" : ""));
            }
            anchors.put(name, new Anchor(resolved, declared, overridden));
        }
        return new Roots(anchors, fileWhat);
    }

    /**
     * The absolute location a {@code @<name>/…} reference names, or
     * {@code null} when the path is not a root reference (a literal
     * {@code @}-initial filename is written {@code ./@…}).
     */
    public Path resolve(String rawPath, String where) {
        if (!rawPath.startsWith("@")) {
            return null;
        }
        int separator = rawPath.indexOf('/');
        String name = separator < 0 ? rawPath.substring(1) : rawPath.substring(1, separator);
        String remainder = separator < 0 ? "" : rawPath.substring(separator + 1);
        if (remainder.isEmpty()) {
            throw fail(where + ": `" + rawPath + "` — a root is a directory; reference a "
                    + "file within it (`@" + name + "/<path>`)");
        }
        Anchor anchor = anchors.get(name);
        if (anchor == null) {
            String declared = anchors.isEmpty()
                    ? "none"
                    : String.join(", ", new TreeSet<>(anchors.keySet()));
            throw fail(where + ": `@" + name + "/` references an undeclared root — "
                    + fileWhat + " declares: " + declared);
        }
        Path resolved = anchor.resolved().resolve(remainder).normalize();
        if (!resolved.startsWith(anchor.resolved())) {
            throw fail(where + ": `" + rawPath + "` escapes its root — the reference "
                    + "resolves above `" + name + ":`; a path below the root needs no "
                    + "`..` climbing, and one above it belongs to a different root");
        }
        used.add(name);
        return resolved;
    }

    /**
     * A declared root referenced by nothing in the file is a dead
     * declaration — most likely a leftover; remove it.
     */
    public void refuseDead() {
        for (String name : anchors.keySet()) {
            if (!used.contains(name)) {
                throw fail(fileWhat + ": root `" + name + ":` is declared but referenced "
                        + "by nothing in the file — remove the dead declaration");
            }
        }
    }

    private static String override(String name) {
        String property = System.getProperty("mavai.root." + name);
        if (property != null) {
            return property;
        }
        return System.getenv(overrideVariable(name));
    }

    private static String overrideVariable(String name) {
        return "MAVAI_ROOT_" + name.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static boolean isAbsolute(String value) {
        return Path.of(value).isAbsolute()
                || value.startsWith("/")
                || value.startsWith("\\")
                || DRIVE_ABSOLUTE.matcher(value).matches();
    }
}
