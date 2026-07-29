package org.mavai.punit.lm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLConnection;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.decl.spi.MessageParts;

/**
 * General multimodal assembly — the common machinery, no vendor
 * shapes: the media capability vocabulary, base64 / data-URI encoding,
 * media-type derivation, and the skeleton that decides
 * text-only-versus-blocks. Each provider adapter supplies its own
 * API-specific block shape and composes it with
 * {@link #contentBlocks}; this class deliberately knows no vendor's
 * wire format.
 */
public final class Media {

    /**
     * Which capability token gates which media kind — single-sourced.
     * {@link MediaKind#FILE} is deliver-to-binding only: it has no
     * model wire form, maps to no token, and is refused at the
     * language-model boundary.
     */
    public static final Map<MediaKind, String> CAPABILITY_FOR = Map.of(
            MediaKind.IMAGE, "image-input",
            MediaKind.DOCUMENT, "document-input",
            MediaKind.AUDIO, "audio-input");

    /** Per-kind media type when the file extension yields no guess. */
    private static final Map<MediaKind, String> MIME_FALLBACK = Map.of(
            MediaKind.IMAGE, "image/png",
            MediaKind.DOCUMENT, "application/pdf",
            MediaKind.AUDIO, "audio/wav");

    private Media() {}

    /** The ordered parts of an LLM input; a lone string or file is one part. */
    public static List<Object> messageParts(Object input) {
        if (input instanceof MessageParts message) {
            return message.parts();
        }
        return List.of(input);
    }

    /** The distinct media kinds an input carries (empty for text-only). */
    public static Set<MediaKind> mediaKindsPresent(Object input) {
        Set<MediaKind> kinds = new LinkedHashSet<>();
        for (Object part : messageParts(input)) {
            if (part instanceof FileInput file) {
                kinds.add(file.kind());
            }
        }
        return kinds;
    }

    public static boolean hasMedia(Object input) {
        return messageParts(input).stream().anyMatch(part -> part instanceof FileInput);
    }

    /** The media type from the file extension, with a per-kind fallback. */
    public static String mimeType(FileInput part) {
        String guess = URLConnection.guessContentTypeFromName(part.path().getFileName().toString());
        if (guess != null) {
            return guess;
        }
        return MIME_FALLBACK.getOrDefault(part.kind(), "application/octet-stream");
    }

    /** The part's bytes as base64 ASCII — the raw form vendors embed. */
    public static String b64(FileInput part) {
        return Base64.getEncoder().encodeToString(part.data());
    }

    /** A {@code data:<mime>;base64,<…>} URI — the form OpenAI-style APIs embed. */
    public static String dataUri(FileInput part) {
        return "data:" + mimeType(part) + ";base64," + b64(part);
    }

    /** The file extension without its dot, for format-declaring wire fields. */
    public static String extension(FileInput part) {
        String name = part.path().getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    /**
     * A defect (not an authoring error) for a block renderer that meets
     * a kind its protocol does not carry — the media capability gate
     * should have refused it.
     */
    public static IllegalStateException unexpectedKind(FileInput part, String protocol) {
        return new IllegalStateException(protocol + " block assembly reached an unsupported "
                + "media kind '" + part.kind().key() + "' — the media capability gate failed "
                + "to refuse it");
    }

    /**
     * The general text-versus-blocks skeleton for a block-based
     * protocol: the plain input when there is no media — byte-identical
     * to a text-only request, so existing contracts are untouched —
     * otherwise an ordered array of typed blocks, text and media
     * interleaved in the authored order.
     */
    public static JsonNode contentBlocks(Object input, Function<FileInput, ObjectNode> mediaBlock) {
        if (!hasMedia(input)) {
            return JsonNodeFactory.instance.textNode(String.valueOf(input));
        }
        ArrayNode blocks = JsonNodeFactory.instance.arrayNode();
        for (Object part : messageParts(input)) {
            if (part instanceof FileInput file) {
                blocks.add(mediaBlock.apply(file));
            } else {
                blocks.add(JsonNodeFactory.instance.objectNode()
                        .put("type", "text")
                        .put("text", String.valueOf(part)));
            }
        }
        return blocks;
    }
}
