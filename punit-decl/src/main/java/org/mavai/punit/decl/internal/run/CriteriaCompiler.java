package org.mavai.punit.decl.internal.run;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.api.criterion.CriterionDecl;
import org.mavai.punit.api.criterion.Decl;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.path.CompiledJsonPath;
import org.mavai.punit.decl.internal.path.PathSyntaxException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.CriterionDeclaration;
import org.mavai.punit.decl.internal.model.FormDeclaration;
import org.mavai.punit.decl.internal.model.InputDeclaration;
import org.mavai.punit.decl.internal.model.PostconditionForm;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Compiles parsed criterion declarations onto punit's own criteria
 * machinery — {@code Criteria.meeting()} / {@code Criteria.empirical()}
 * declarations with postconditions appended in declaration order —
 * adding no statistics: thresholds, confidence, and risk claims travel
 * into the same decls the builder API uses, and the engine's existing
 * evaluation does the rest.
 *
 * <p>Selection expressions compile eagerly here (RFC 9535 JSONPath for
 * {@code json}/{@code yaml} views, XPath 1.0 for {@code xml} views), so
 * a malformed expression refuses at load, never mid-run.
 */
final class CriteriaCompiler {

    private final ContractDeclaration declaration;
    private final StockViews views;
    private final AtomicReference<Object> currentInput;
    private final Map<String, Double> tolerateOverrides;
    private final Double power;
    private final Map<Object, List<FormDeclaration>> expectations = new IdentityHashMap<>();

    CriteriaCompiler(ContractDeclaration declaration, StockViews views,
            AtomicReference<Object> currentInput,
            Map<String, Double> tolerateOverrides, Double power) {
        this.declaration = declaration;
        this.views = views;
        this.currentInput = currentInput;
        this.tolerateOverrides = tolerateOverrides;
        this.power = power;
        for (InputDeclaration input : declaration.inputs()) {
            if (input.hasExpectations()) {
                expectations.put(input.value(), input.expected());
            }
        }
    }

    Criteria<String> compile() {
        List<Decl<String>> decls = new ArrayList<>();
        for (CriterionDeclaration criterion : declaration.criteria()) {
            decls.add(compileCriterion(criterion));
        }
        if (decls.size() == 1) {
            return Criteria.of(decls.get(0));
        }
        @SuppressWarnings("unchecked")
        Decl<String>[] array = decls.toArray(Decl[]::new);
        return Criteria.of(array);
    }

    private Decl<String> compileCriterion(CriterionDeclaration criterion) {
        CriterionDecl<String> decl = criterion.threshold() != null
                ? Criteria.meeting().<String>passRate(criterion.threshold())
                : Criteria.empirical().passRate();
        decl = decl.name(criterion.name());
        // A declared threshold is judged at the framework confidence by
        // punit-core's design; a non-default file-level confidence with
        // thresholded criteria refuses at run time before compilation.
        Double tolerate = tolerateOverrides.containsKey(criterion.name())
                ? tolerateOverrides.get(criterion.name())
                : criterion.tolerate();
        if (tolerate != null) {
            decl = decl.tolerating(tolerate);
            if (power != null) {
                decl = decl.atPower(power);
            }
        }
        if (criterion.confidence() != null) {
            decl = decl.atConfidence(criterion.confidence());
        }
        if (criterion.contractRef() != null) {
            decl = criterion.thresholdOrigin() != null
                    ? decl.contractRef(origin(criterion.thresholdOrigin()), criterion.contractRef())
                    : decl.contractRef(criterion.contractRef());
        }
        for (FormDeclaration form : criterion.forms()) {
            Check check = compileForm(form);
            decl = decl.satisfies(check.name(), response -> check.evaluate((String) response));
        }
        if (!expectations.isEmpty() && declaration.criteria().size() == 1) {
            decl = decl.satisfies("input-specific expectations",
                    response -> evaluateExpectations((String) response));
        }
        return decl;
    }

    private static ThresholdOrigin origin(String key) {
        return ThresholdOrigin.valueOf(key.toUpperCase(Locale.ROOT));
    }

    private Outcome<?> evaluateExpectations(String response) {
        Object input = currentInput.get();
        List<FormDeclaration> forms = expectations.get(input);
        if (forms == null) {
            return Outcome.ok();
        }
        for (FormDeclaration form : forms) {
            Check check = compileExpectedForm(form, input);
            Outcome<?> result = check.evaluate(response);
            if (result instanceof Outcome.Fail<?>) {
                return result;
            }
        }
        return Outcome.ok();
    }

    // ── Form compilation ──────────────────────────────────────────

    /** One compiled postcondition form: a name and its evaluation. */
    record Check(String name, Evaluation evaluation) {
        Outcome<?> evaluate(String response) {
            return evaluation.evaluate(response);
        }
    }

    interface Evaluation {
        Outcome<?> evaluate(String response);
    }

    Check compileForm(FormDeclaration form) {
        return compileForm(form, describe(form));
    }

    private Check compileExpectedForm(FormDeclaration form, Object input) {
        // Expected forms compile per declaration at load time in
        // validateEagerly(); this lookup re-derives the same compiled
        // shape with the input named in the failure reason.
        return compileForm(form, "expected for input '" + Display.of(input) + "': " + describe(form));
    }

    private Check compileForm(FormDeclaration form, String name) {
        if (form.form() == PostconditionForm.SATISFIES) {
            throw new ContractConfigurationException(
                    "`satisfies: " + form.argument() + "` names a check registered in code — "
                            + "the named-check registry arrives with the bindings-artefact phase "
                            + "of the declarative surface");
        }
        if (form.form() == PostconditionForm.PARSES) {
            String view = (String) form.argument();
            return new Check(name, response -> {
                Outcome<Object> parsed = views.view(view, response);
                return parsed instanceof Outcome.Fail<?> fail ? fail : Outcome.ok();
            });
        }
        StringMatch match = stringMatch(form);
        if (form.view().equals(FormDeclaration.RAW_VIEW)) {
            return new Check(name, response -> match.holds(response)
                    ? Outcome.ok()
                    : Outcome.fail("postcondition", name + " — response did not satisfy it"));
        }
        String transformation = views.transformation(form.view());
        if (form.path() == null) {
            return new Check(name, response -> {
                Outcome<Object> parsed = views.view(form.view(), response);
                if (parsed instanceof Outcome.Fail<?> fail) {
                    return fail;
                }
                Object value = ((Outcome.Ok<Object>) parsed).value();
                if (!(value instanceof String text)) {
                    return Outcome.fail("postcondition", name + " — the view's value is not "
                            + "text; a string form needs a text subject");
                }
                return match.holds(text)
                        ? Outcome.ok()
                        : Outcome.fail("postcondition", name + " — subject did not satisfy it");
            });
        }
        Selection selection = compileSelection(transformation, form.path());
        return new Check(name, response -> {
            Outcome<Object> parsed = views.view(form.view(), response);
            if (parsed instanceof Outcome.Fail<?> fail) {
                return fail;
            }
            Object value = ((Outcome.Ok<Object>) parsed).value();
            List<Object> selected;
            try {
                selected = selection.select(value);
            } catch (RuntimeException error) {
                return Outcome.fail("postcondition", name + " — selection failed: " + error.getMessage());
            }
            if (selected.isEmpty()) {
                return Outcome.fail("postcondition", name + " — path selected nothing");
            }
            for (Object candidate : selected) {
                String text = comparable(candidate);
                if (text == null) {
                    return Outcome.fail("postcondition", name + " — path selected structure, "
                            + "not a comparable value");
                }
                if (!match.holds(text)) {
                    return Outcome.fail("postcondition",
                            name + " — selected value '" + Display.of(text) + "' did not satisfy it");
                }
            }
            return Outcome.ok();
        });
    }

    /** §II.10 value comparison: strings by content, primitives by JSON text, structure refused. */
    private static String comparable(Object candidate) {
        if (candidate instanceof String text) {
            return text;
        }
        if (candidate instanceof Number || candidate instanceof Boolean) {
            return String.valueOf(candidate);
        }
        if (candidate == null) {
            return "null";
        }
        return null;
    }

    // ── String forms ──────────────────────────────────────────────

    private interface StringMatch {
        boolean holds(String subject);
    }

    private StringMatch stringMatch(FormDeclaration form) {
        return switch (form.form()) {
            case EQUALS -> {
                String expected = (String) form.argument();
                yield expected::equals;
            }
            case ONE_OF -> {
                @SuppressWarnings("unchecked")
                List<String> options = (List<String>) form.argument();
                yield options::contains;
            }
            case CONTAINS -> {
                String needle = (String) form.argument();
                yield subject -> subject.contains(needle);
            }
            case MATCHES -> {
                Pattern pattern;
                try {
                    pattern = Pattern.compile((String) form.argument());
                } catch (PatternSyntaxException error) {
                    throw new ContractConfigurationException(
                            "`matches: " + form.argument() + "` is not a valid regular "
                                    + "expression: " + error.getDescription());
                }
                yield subject -> pattern.matcher(subject).find();
            }
            default -> throw new IllegalStateException("not a string form: " + form.form());
        };
    }

    // ── Selection expressions ─────────────────────────────────────

    private interface Selection {
        List<Object> select(Object viewValue);
    }

    private Selection compileSelection(String transformation, String expression) {
        if (transformation.equals("xml")) {
            XPathExpression compiled;
            try {
                compiled = XPathFactory.newInstance().newXPath().compile(expression);
            } catch (XPathExpressionException error) {
                throw new ContractConfigurationException(
                        "`path: " + expression + "` is not a valid XPath 1.0 expression: "
                                + error.getMessage());
            }
            return viewValue -> selectXml(compiled, (Document) viewValue);
        }
        CompiledJsonPath compiled;
        try {
            compiled = CompiledJsonPath.compile(expression);
        } catch (PathSyntaxException error) {
            throw new ContractConfigurationException(
                    "`path: " + expression + "` is not a valid JSONPath (RFC 9535) expression: "
                            + error.getMessage());
        }
        return compiled::select;
    }

    private static List<Object> selectXml(XPathExpression expression, Document document) {
        try {
            NodeList nodes = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
            List<Object> values = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                values.add(nodes.item(i).getTextContent());
            }
            return values;
        } catch (XPathExpressionException notANodeSet) {
            try {
                return List.of(expression.evaluate(document));
            } catch (XPathExpressionException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
        }
    }

    // ── Load-time validation of every compiled shape ──────────────

    /**
     * Forces compilation of every form (criterion-level and per-input)
     * so selection expressions, regexes, and unsupported constructs
     * refuse at load — a configuration defect never costs a sample.
     */
    void validateEagerly() {
        for (CriterionDeclaration criterion : declaration.criteria()) {
            for (FormDeclaration form : criterion.forms()) {
                compileForm(form);
            }
        }
        for (InputDeclaration input : declaration.inputs()) {
            for (FormDeclaration form : input.expected()) {
                compileExpectedForm(form, input.value());
            }
        }
    }

    private String describe(FormDeclaration form) {
        StringBuilder description = new StringBuilder();
        if (!form.view().equals(FormDeclaration.RAW_VIEW)) {
            description.append(form.view()).append(": ");
        }
        if (form.path() != null) {
            description.append(form.path()).append(' ');
        }
        description.append(form.form().key()).append(' ').append(Display.of(form.argument()));
        return description.toString();
    }

    static final class Display {
        private Display() {}

        static String of(Object value) {
            String text = String.valueOf(value);
            return text.length() <= 60 ? text : text.substring(0, 57) + "...";
        }
    }
}
