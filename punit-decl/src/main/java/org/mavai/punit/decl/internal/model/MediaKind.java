package org.mavai.punit.decl.internal.model;

/**
 * The declared content class of a file-sourced media input part — a
 * closed set, named by the author (never sniffed) and parsed once at
 * the contract boundary.
 */
public enum MediaKind {
    FILE("file"),
    AUDIO("audio"),
    IMAGE("image"),
    DOCUMENT("document");

    private final String key;

    MediaKind(String key) {
        this.key = key;
    }

    /** The part key as written in the contract file. */
    public String key() {
        return key;
    }

    /** The kind for a part key, or {@code null} when the key is no media kind. */
    public static MediaKind forKey(String key) {
        for (MediaKind kind : values()) {
            if (kind.key.equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
