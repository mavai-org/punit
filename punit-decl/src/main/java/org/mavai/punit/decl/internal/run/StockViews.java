package org.mavai.punit.decl.internal.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.model.ContractDeclaration;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.xml.sax.InputSource;

/**
 * The declared views at run time: each view is a stock transformation
 * of the response — {@code json}, {@code xml}, {@code yaml} — computed
 * lazily on first use, at most once per response, and shared by every
 * consumer across postconditions and criteria. A transformation failure
 * is the trial's failure with the transform-failure reason, never an
 * abort.
 *
 * <p>Memoisation assumes the engine evaluates one sample's criteria
 * before the next sample's response arrives (punit's sequential
 * engine); the cache resets whenever a new response instance appears.
 */
final class StockViews {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, String> transforms;

    private Object currentResponse;
    private Map<String, Outcome<Object>> cache = new HashMap<>();

    StockViews(ContractDeclaration declaration) {
        this.transforms = declaration.transforms();
        for (Map.Entry<String, String> view : transforms.entrySet()) {
            String transformation = view.getValue();
            if (!transformation.equals("json") && !transformation.equals("xml")
                    && !transformation.equals("yaml")) {
                throw new ContractConfigurationException(
                        "view '" + view.getKey() + "' names transformation '" + transformation
                                + "', which is not a stock one (json, xml, yaml) — registered "
                                + "transformations arrive with the bindings-artefact phase of "
                                + "the declarative surface");
            }
        }
    }

    /** The transformation a view applies (stock names only in this phase). */
    String transformation(String view) {
        return transforms.get(view);
    }

    /** The view's value for this response — computed once, shared, memoised. */
    synchronized Outcome<Object> view(String name, String response) {
        if (currentResponse != response) {
            currentResponse = response;
            cache = new HashMap<>();
        }
        return cache.computeIfAbsent(name, view -> compute(view, response));
    }

    private Outcome<Object> compute(String name, String response) {
        String transformation = transforms.get(name);
        try {
            return switch (transformation) {
                case "json" -> Outcome.ok(JSON.readValue(response, Object.class));
                case "yaml" -> Outcome.ok(yaml(response));
                case "xml" -> Outcome.ok(xml(response));
                default -> throw new IllegalStateException("unreachable: " + transformation);
            };
        } catch (Exception failure) {
            return Outcome.fail("transform-failure",
                    "view '" + name + "' (" + transformation + "): " + firstLine(failure));
        }
    }

    private Object yaml(String response) {
        Load load = new Load(LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .build());
        Iterator<Object> documents = load.loadAllFromString(response).iterator();
        if (!documents.hasNext()) {
            return null;
        }
        Object document = documents.next();
        if (documents.hasNext()) {
            throw new IllegalArgumentException("multi-document stream");
        }
        requireStringKeys(document);
        return document;
    }

    private void requireStringKeys(Object node) {
        if (node instanceof Map<?, ?> mapping) {
            for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException(
                            "mapping key is not a string: " + entry.getKey());
                }
                requireStringKeys(entry.getValue());
            }
        } else if (node instanceof List<?> sequence) {
            for (Object element : sequence) {
                requireStringKeys(element);
            }
        }
    }

    private Object xml(String response) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The response under transformation is untrusted output from a
        // stochastic service: no doctypes, no external entities.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(response)));
    }

    private static String firstLine(Exception failure) {
        String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
