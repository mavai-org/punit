package org.mavai.punit.decl.parser;

import static org.mavai.punit.decl.parser.Yaml.fail;
import static org.mavai.punit.decl.parser.Yaml.requireMapping;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.mavai.punit.decl.model.FileInput;
import org.mavai.punit.decl.model.Form;
import org.mavai.punit.decl.model.FormDeclaration;
import org.mavai.punit.decl.model.InputExpectation;
import org.mavai.punit.decl.model.MediaKind;
import org.mavai.punit.decl.model.MessageParts;

/**
 * The {@code inputs:} block: scalars, flat argument lists, file-sourced
 * parts, ordered part lists, and per-input {@code expected:} entries.
 * File-sourced parts resolve relative to the contract file and are read
 * once at load time, so an unreadable file refuses before any
 * invocation.
 */
final class InputsParser {

    private static final String PART_KEYS = "text/audio/image/document/file";

    private InputsParser() {}

    record Result(List<Object> inputs, List<InputExpectation> expectations) {}

    static Result parse(Object value, Map<String, String> views, Path baseDir) {
        if (!(value instanceof List<?> entries) || entries.isEmpty()) {
            throw fail("`inputs:` must be a non-empty list");
        }
        List<Object> inputs = new ArrayList<>();
        List<InputExpectation> expectations = new ArrayList<>();
        int index = 0;
        for (Object entry : entries) {
            index++;
            String where = "inputs entry " + index;
            if (entry instanceof Map<?, ?> mapping
                    && mapping.keySet().stream().map(String::valueOf).collect(Collectors.toSet())
                            .equals(Set.of("input", "expected"))) {
                Map<String, Object> pair = requireMapping(entry, where);
                Object inputValue = normalised(pair.get("input"), where, baseDir);
                String expectedWhere = "expected for input '" + display(inputValue) + "'";
                Object expected = pair.get("expected");
                List<?> expectedEntries = expected instanceof Map<?, ?> single
                        ? List.of(single)
                        : expected instanceof List<?> list ? list : null;
                if (expectedEntries == null || expectedEntries.isEmpty()) {
                    throw fail(expectedWhere + ": `expected:` is a form or a non-empty list of forms");
                }
                List<FormDeclaration> forms = new ArrayList<>();
                for (Object formEntry : expectedEntries) {
                    forms.add(FormParser.parse(requireMapping(formEntry, expectedWhere), expectedWhere, views));
                }
                for (FormDeclaration declaration : forms) {
                    if (declaration.form() == Form.PARSES) {
                        throw fail(expectedWhere + ": `parses:` is a criterion-level form");
                    }
                }
                expectations.add(new InputExpectation(inputs.size(), inputValue, forms));
                inputs.add(inputValue);
            } else {
                inputs.add(normalised(entry, where, baseDir));
            }
        }
        return new Result(inputs, expectations);
    }

    /**
     * One input value: a scalar, a flat list of scalars (an argument
     * tuple), a file-sourced part, or a list of parts (an ordered
     * multimodal message; a single-part list is the bare part).
     */
    private static Object normalised(Object entry, String where, Path baseDir) {
        if (isScalar(entry)) {
            return entry;
        }
        if (entry instanceof Map<?, ?>) {
            return part(requireMapping(entry, where), where, baseDir);
        }
        if (entry instanceof List<?> list) {
            if (list.isEmpty()) {
                throw fail(where + ": a list-valued input must be non-empty");
            }
            if (list.stream().allMatch(InputsParser::isScalar)) {
                return List.copyOf(list);
            }
            if (list.stream().allMatch(item -> item instanceof Map<?, ?>)) {
                List<Object> parts = new ArrayList<>();
                for (Object item : list) {
                    parts.add(part(requireMapping(item, where), where, baseDir));
                }
                return parts.size() == 1 ? parts.get(0) : new MessageParts(parts);
            }
            throw fail(where + ": a list-valued input is a flat list of scalars (splatted across "
                    + "the binding's parameters) or a list of parts (text/media), not a mix");
        }
        throw fail(where + ": an input is a scalar, a flat list of scalars, or a file-sourced "
                + "part (" + PART_KEYS + "), got " + typeName(entry));
    }

    /** A single-key input part mapping — text or a media reference. */
    private static Object part(Map<String, Object> mapping, String where, Path baseDir) {
        if (mapping.size() != 1) {
            String keys = mapping.keySet().stream().sorted().collect(Collectors.joining(", "));
            throw fail(where + ": an input part is a single-key mapping (" + PART_KEYS + "), got "
                    + "keys {" + keys + "} — an `{input, expected}` entry attaches expectations "
                    + "instead");
        }
        Map.Entry<String, Object> single = mapping.entrySet().iterator().next();
        String key = single.getKey();
        Object value = single.getValue();
        if (key.equals("text")) {
            return textPart(value, where, baseDir);
        }
        MediaKind kind = MediaKind.forKey(key);
        if (kind != null) {
            if (!(value instanceof String rawPath) || rawPath.isEmpty()) {
                throw fail(where + ": `" + key + ":` is a file path string");
            }
            Resolved resolved = resolveAndRead(rawPath, where, baseDir);
            return new FileInput(resolved.path(), kind, resolved.data());
        }
        throw fail(where + ": unknown input part `" + key + ":` — a part is one of " + PART_KEYS
                + ", or an `{input, expected}` entry");
    }

    /** A {@code text:} part — an inline string, or {@code {file: <path>}} decoded as UTF-8 text. */
    private static String textPart(Object value, String where, Path baseDir) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> reference
                && reference.size() == 1
                && reference.containsKey("file")
                && reference.get("file") instanceof String rawPath) {
            Resolved resolved = resolveAndRead(rawPath, where, baseDir);
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .decode(java.nio.ByteBuffer.wrap(resolved.data()))
                        .toString();
            } catch (CharacterCodingException error) {
                throw fail(where + ": `text:` file is not valid UTF-8 text: " + error, error);
            }
        }
        throw fail(where + ": `text:` is a string or a `{file: <path>}` mapping");
    }

    private record Resolved(Path path, byte[] data) {}

    /**
     * Resolves a file-sourced part's path relative to the contract file
     * and reads it — resolution is never against the working directory,
     * and a file that cannot be read is a load-time authoring error.
     */
    private static Resolved resolveAndRead(String rawPath, String where, Path baseDir) {
        if (baseDir == null) {
            throw fail(where + ": a file-sourced input needs a contract loaded from disk to "
                    + "resolve '" + rawPath + "' relative to it");
        }
        Path resolved = baseDir.resolve(rawPath).normalize();
        try {
            return new Resolved(resolved, Files.readAllBytes(resolved));
        } catch (IOException error) {
            throw fail(where + ": cannot read input file " + resolved + ": " + error.getMessage(),
                    error);
        }
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String display(Object inputValue) {
        String text = String.valueOf(inputValue);
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}
