package org.mavai.punit.lm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The module's one JSON seam: tree conversion, request serialisation,
 * response parsing, and the response-schema identity fingerprint.
 */
final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private Json() {}

    /** The value as a Jackson tree (a YAML-parsed mapping, typically). */
    static JsonNode node(Object value) {
        return MAPPER.valueToTree(value);
    }

    /** The tree serialised for the wire. */
    static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "request body serialisation failed: " + error.getMessage(), error);
        }
    }

    /** A delivered body as a tree; a non-JSON body is a delivery failure. */
    static JsonNode read(String body, String providerName) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException error) {
            throw new ServiceDeliveryException("service delivered a response body that is not "
                    + "JSON (the " + providerName + " shape requires it): "
                    + error.getOriginalMessage());
        }
    }

    /**
     * The response schema's identity fingerprint: SHA-256 over the
     * key-sorted canonical serialisation, hex-encoded — what joins
     * provenance in place of the schema's bulk.
     */
    static String fingerprint(Object schema) {
        try {
            byte[] canonical = CANONICAL.writeValueAsBytes(schema);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "response-schema fingerprinting failed: " + error.getMessage(), error);
        }
    }

    /** A bounded excerpt of a response body for investigable messages. */
    static String excerpt(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
