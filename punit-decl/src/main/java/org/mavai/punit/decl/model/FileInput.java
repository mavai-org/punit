package org.mavai.punit.decl.model;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * A file-sourced input part, delivered to a bound service verbatim: the
 * resolved path, the author-declared kind, and the bytes — read once at
 * load time, so an unreadable file refuses before any invocation and
 * the content participates in input identity.
 *
 * @param path the resolved path
 * @param kind the declared content class
 * @param data the file's bytes
 */
public record FileInput(Path path, MediaKind kind, byte[] data) {

    public FileInput {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FileInput that
                && path.equals(that.path)
                && kind == that.kind
                && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * path.hashCode() + kind.hashCode()) + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "FileInput[path=" + path + ", kind=" + kind + ", " + data.length + " bytes]";
    }
}
