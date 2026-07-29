package org.mavai.punit.decl.internal.run;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
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
import org.mavai.punit.decl.internal.model.NumericValue;
import org.mavai.punit.decl.internal.model.PostconditionForm;
import org.mavai.punit.decl.internal.model.SetElements;
import org.mavai.punit.decl.internal.model.SetOfDeclaration;
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
    private final BindingsRegistry registry;
    private final Map<Object, List<FormDeclaration>> expectations = new IdentityHashMap<>();

    CriteriaCompiler(ContractDeclaration declaration, StockViews views,
            AtomicReference<Object> currentInput,
            Map<String, Double> tolerateOverrides, Double power, BindingsRegistry registry) {
        this.declaration = declaration;
        this.views = views;
        this.currentInput = currentInput;
        this.tolerateOverrides = tolerateOverrides;
        this.power = power;
        this.registry = registry;
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
            if (form.optional()) {
                decl = decl.optional();
            }
        }
        if (!expectations.isEmpty() && declaration.criteria().size() == 1) {
            // Each per-input expectation is its own check, dispatching on
            // the sample's input — so an optional mark counts individually
            // against the criterion's budget, exactly as a global check.
            // A check passes neutrally on the inputs it does not apply to.
            java.util.List<org.mavai.punit.decl.internal.model.InputDeclaration> inputs =
                    declaration.inputs();
            for (int index = 0; index < inputs.size(); index++) {
                Object inputValue = inputs.get(index).value();
                for (FormDeclaration form : inputs.get(index).expected()) {
                    Check check = compileExpectedForm(form, inputValue);
                    String name = "input " + index + ": " + check.name();
                    // Identity, not equality: inputs with equal values are
                    // never conflated — the dispatched object IS the list's.
                    decl = decl.satisfies(name, response ->
                            inputValue == currentInput.get()
                                    ? check.evaluate((String) response)
                                    : Outcome.ok());
                    if (form.optional()) {
                        decl = decl.optional();
                    }
                }
            }
        }
        if (criterion.optionalSlack() != null) {
            decl = decl.optionalSlack(criterion.optionalSlack());
        }
        return decl;
    }

    private static ThresholdOrigin origin(String key) {
        return ThresholdOrigin.valueOf(key.toUpperCase(Locale.ROOT));
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
            String checkName = (String) form.argument();
            if (!registry.hasCheck(checkName)) {
                // Force the registry's refusal, which names the known checks.
                registry.applyCheck(checkName, "");
            }
            return new Check(name, response -> {
                Object subject = response;
                if (!form.view().equals(FormDeclaration.RAW_VIEW)) {
                    Outcome<Object> parsed = views.view(form.view(), response);
                    if (parsed instanceof Outcome.Fail<?> fail) {
                        return fail;
                    }
                    subject = ((Outcome.Ok<Object>) parsed).value();
                }
                return registry.applyCheck(checkName, subject);
            });
        }
        if (form.form() == PostconditionForm.PARSES) {
            String view = (String) form.argument();
            return new Check(name, response -> {
                Outcome<Object> parsed = views.view(view, response);
                return parsed instanceof Outcome.Fail<?> fail ? fail : Outcome.ok();
            });
        }
        if (form.form().collective()) {
            return compileSetForm(form, name);
        }
        if (form.form().scalarValue()) {
            return compileScalarValueForm(form, name);
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

    // ── Value-comparison forms ────────────────────────────────────
    //
    // These judge typed values, not text: a number by decimal
    // comparison, a selection by strict JSON-value equality. The scalar
    // forms judge one subject (fanned across a multi-valued selection,
    // like the string forms — but judging the selected value itself,
    // never a text projection); the collective set forms judge the
    // whole selection at once.

    /** A scalar value judgement: {@code null} = holds, else the failure reason. */
    private interface ValueJudge {
        String failure(Object value);
    }

    /** A collective judgement over the whole selection. */
    private interface CollectiveJudge {
        String failure(List<Object> selected);
    }

    private Check compileScalarValueForm(FormDeclaration form, String name) {
        ValueJudge judge = valueJudge(form);
        if (form.view().equals(FormDeclaration.RAW_VIEW)) {
            return new Check(name, response -> judged(name, judge.failure(response)));
        }
        if (form.path() == null) {
            return new Check(name, response -> {
                Outcome<Object> parsed = views.view(form.view(), response);
                if (parsed instanceof Outcome.Fail<?> fail) {
                    return fail;
                }
                return judged(name, judge.failure(((Outcome.Ok<Object>) parsed).value()));
            });
        }
        Selection selection = compileSelection(views.transformation(form.view()), form.path());
        // is-null is null-or-absent: a path that selects nothing holds.
        boolean emptySelectionHolds = form.form() == PostconditionForm.IS_NULL;
        return new Check(name, response -> {
            Outcome<Object> parsed = views.view(form.view(), response);
            if (parsed instanceof Outcome.Fail<?> fail) {
                return fail;
            }
            List<Object> selected;
            try {
                selected = selection.select(((Outcome.Ok<Object>) parsed).value());
            } catch (RuntimeException error) {
                return Outcome.fail("postcondition", name + " — selection failed: " + error.getMessage());
            }
            if (selected.isEmpty()) {
                return emptySelectionHolds
                        ? Outcome.ok()
                        : Outcome.fail("postcondition", name + " — path selected nothing");
            }
            for (Object candidate : selected) {
                String failure = judge.failure(candidate);
                if (failure != null) {
                    return Outcome.fail("postcondition", name + " — " + failure);
                }
            }
            return Outcome.ok();
        });
    }

    private Check compileSetForm(FormDeclaration form, String name) {
        // The parser guarantees a declared view and a path — a collection
        // only exists as a selection. The empty selection is the empty
        // collection: count-equals: 0 holds, a non-empty operand fails.
        Selection selection = compileSelection(views.transformation(form.view()), form.path());
        CollectiveJudge judge = collectiveJudge(form);
        return new Check(name, response -> {
            Outcome<Object> parsed = views.view(form.view(), response);
            if (parsed instanceof Outcome.Fail<?> fail) {
                return fail;
            }
            List<Object> selected;
            try {
                selected = selection.select(((Outcome.Ok<Object>) parsed).value());
            } catch (RuntimeException error) {
                return Outcome.fail("postcondition", name + " — selection failed: " + error.getMessage());
            }
            return judged(name, judge.failure(selected));
        });
    }

    private static Outcome<?> judged(String name, String failure) {
        return failure == null
                ? Outcome.ok()
                : Outcome.fail("postcondition", name + " — " + failure);
    }

    private ValueJudge valueJudge(FormDeclaration form) {
        if (form.form().numeric()) {
            return numericJudge(form);
        }
        return switch (form.form()) {
            case NOT_EQUALS -> {
                String excluded = (String) form.argument();
                yield value -> {
                    if (!(value instanceof String text)) {
                        return textTypeFailure("not-equals", value);
                    }
                    return text.equals(excluded)
                            ? "response equals the excluded \"" + excluded + "\""
                            : null;
                };
            }
            case EQUALS_CI -> {
                String expected = (String) form.argument();
                String foldedExpected = folded(expected);
                yield value -> {
                    if (!(value instanceof String text)) {
                        return textTypeFailure("equals-ci", value);
                    }
                    return folded(text).equals(foldedExpected)
                            ? null
                            : "response does not equal \"" + expected
                                    + "\" (case/whitespace-insensitively)";
                };
            }
            case IS_NULL -> value -> value == null
                    ? null
                    : "value " + Display.of(value) + " is not null";
            case IS -> {
                boolean operand = (Boolean) form.argument();
                yield value -> {
                    if (value instanceof Boolean actual) {
                        return actual == operand ? null : "value is " + actual + ", not " + operand;
                    }
                    return "subject " + Display.of(value) + " is not a boolean — the form "
                            + "judges JSON true/false by identity";
                };
            }
            default -> throw new IllegalStateException("not a scalar value form: " + form.form());
        };
    }

    private ValueJudge numericJudge(FormDeclaration form) {
        BigDecimal operand = NumericValue.of(form.argument());
        IntPredicate holds = switch (form.form()) {
            case EQ -> comparison -> comparison == 0;
            case NE -> comparison -> comparison != 0;
            case LT -> comparison -> comparison < 0;
            case LE -> comparison -> comparison <= 0;
            case GT -> comparison -> comparison > 0;
            case GE -> comparison -> comparison >= 0;
            default -> throw new IllegalStateException("not a numeric form: " + form.form());
        };
        String key = form.form().key();
        String operandDisplay = Display.of(form.argument());
        return value -> {
            BigDecimal actual = NumericValue.of(value);
            if (actual == null) {
                return "subject " + Display.of(value) + " is not a number — a numeric form "
                        + "judges a number or a numeric string";
            }
            return holds.test(actual.compareTo(operand))
                    ? null
                    : "value " + Display.of(value) + " is not " + key + " " + operandDisplay;
        };
    }

    private static String textTypeFailure(String form, Object value) {
        return form + ": subject " + Display.of(value) + " is not text — a string form judges text";
    }

    private static final Pattern WHITESPACE_RUN =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * The equals-ci normalisation, exactly: Unicode case-fold, trim, and
     * internal-whitespace-run collapse to a single space — nothing more.
     * Upper-then-lower is Java's realisation of the full case fold
     * ({@code ß} → {@code ss}), held to the family semantics by test.
     */
    private static String folded(String text) {
        String cased = text.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
        return WHITESPACE_RUN.matcher(cased.strip()).replaceAll(" ");
    }

    private CollectiveJudge collectiveJudge(FormDeclaration form) {
        return switch (form.form()) {
            case EQUALS_SET -> {
                List<?> operand = (List<?>) form.argument();
                Map<Object, Long> expected = SetElements.multiset(operand);
                yield selected -> SetElements.multiset(selected).equals(expected)
                        ? null
                        : "selected values " + Display.of(selected) + " do not equal "
                                + Display.of(operand) + " as a multiset";
            }
            case CONTAINS_SET -> {
                List<?> operand = (List<?>) form.argument();
                Map<Object, Long> expected = SetElements.multiset(operand);
                yield selected -> {
                    Map<Object, Long> actual = SetElements.multiset(selected);
                    boolean missing = expected.entrySet().stream()
                            .anyMatch(entry -> actual.getOrDefault(entry.getKey(), 0L) < entry.getValue());
                    return missing
                            ? "selected values " + Display.of(selected) + " do not contain "
                                    + "all of " + Display.of(operand)
                            : null;
                };
            }
            case COUNT_EQUALS -> {
                int expected = ((Number) form.argument()).intValue();
                yield selected -> selected.size() == expected
                        ? null
                        : "path selected " + selected.size() + " value(s), not " + expected;
            }
            case SET_OF -> setOfJudge((SetOfDeclaration) form.argument());
            default -> throw new IllegalStateException("not a set form: " + form.form());
        };
    }

    /**
     * The graded set claim, judged by membership — a set is a set.
     * Holds iff every required member appears in the selection, at
     * least min-present distinct optional members appear, and — under
     * refuse-extras — every selected element is a declared member.
     * Duplicates collapse to membership on both sides: a subject
     * element appearing twice is one member present, never an extra.
     * The failure reason states the arithmetic — the missing required
     * members, the present-versus-floor count, and any extras — each
     * list bounded.
     */
    private static CollectiveJudge setOfJudge(SetOfDeclaration claim) {
        Map<Object, Object> requiredKeys = memberKeys(claim.required());
        Map<Object, Object> optionalKeys = memberKeys(claim.optional());
        return selected -> {
            Map<Object, Object> selectedKeys = new LinkedHashMap<>();
            for (Object value : selected) {
                selectedKeys.putIfAbsent(SetElements.key(value), value);
            }
            List<Object> missing = new ArrayList<>();
            requiredKeys.forEach((key, member) -> {
                if (!selectedKeys.containsKey(key)) {
                    missing.add(member);
                }
            });
            long present = optionalKeys.keySet().stream().filter(selectedKeys::containsKey).count();
            List<Object> extras = new ArrayList<>();
            selectedKeys.forEach((key, value) -> {
                if (!requiredKeys.containsKey(key) && !optionalKeys.containsKey(key)) {
                    extras.add(value);
                }
            });
            List<String> parts = new ArrayList<>();
            if (!missing.isEmpty()) {
                parts.add("missing required: " + memberDisplay(missing));
            }
            if (present < claim.minPresent()) {
                parts.add("optional members present " + present + " of "
                        + claim.optional().size() + " (min-present " + claim.minPresent() + ")");
            }
            if (claim.refuseExtras() && !extras.isEmpty()) {
                parts.add("extras: " + memberDisplay(extras));
            }
            return parts.isEmpty() ? null : "set-of: " + String.join("; ", parts);
        };
    }

    /** The declared members by element key, insertion order kept for display. */
    private static Map<Object, Object> memberKeys(List<Object> members) {
        Map<Object, Object> keys = new LinkedHashMap<>();
        for (Object member : members) {
            keys.putIfAbsent(SetElements.key(member), member);
        }
        return keys;
    }

    /** A bounded member listing for reasons: the first few, the rest counted. */
    private static String memberDisplay(List<Object> members) {
        int limit = 3;
        String shown = members.stream()
                .limit(limit)
                .map(Display::of)
                .collect(Collectors.joining(", "));
        int remainder = members.size() - limit;
        return remainder <= 0 ? shown : shown + " (+" + remainder + " more)";
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
        // Stock views pin the language by transformation; a registered
        // view's expression selects its own language by syntax — a
        // $-rooted expression is JSONPath (the RFC mandates the root),
        // anything else XPath 1.0.
        boolean stock = transformation.equals("json") || transformation.equals("yaml")
                || transformation.equals("xml");
        boolean jsonPath = stock ? !transformation.equals("xml") : expression.startsWith("$");
        if (!jsonPath) {
            XPathExpression compiled;
            try {
                compiled = XPathFactory.newInstance().newXPath().compile(expression);
            } catch (XPathExpressionException error) {
                throw new ContractConfigurationException(
                        "`path: " + expression + "` is not a valid XPath 1.0 expression: "
                                + error.getMessage());
            }
            return viewValue -> {
                if (!(viewValue instanceof org.w3c.dom.Node node)) {
                    throw new IllegalArgumentException("the view's value is not a parsed XML "
                            + "document — an XPath selection needs one");
                }
                return selectXml(compiled, node);
            };
        }
        CompiledJsonPath compiled;
        try {
            compiled = CompiledJsonPath.compile(expression);
        } catch (PathSyntaxException error) {
            throw new ContractConfigurationException(
                    "`path: " + expression + "` is not a valid JSONPath (RFC 9535) expression: "
                            + error.getMessage());
        }
        return viewValue -> {
            if (!(viewValue instanceof Map) && !(viewValue instanceof List)) {
                throw new IllegalArgumentException("the view's value is not a JSON-model "
                        + "structure — a JSONPath selection needs a mapping or sequence");
            }
            return compiled.select(viewValue);
        };
    }

    private static List<Object> selectXml(XPathExpression expression, org.w3c.dom.Node document) {
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
        description.append(form.form().key()).append(' ').append(argumentDisplay(form));
        return description.toString();
    }

    /**
     * The form's operand for a check name: the composite claim as its
     * summary arithmetic — never a raw record dump — everything else
     * bounded verbatim.
     */
    private static String argumentDisplay(FormDeclaration form) {
        if (form.argument() instanceof SetOfDeclaration claim) {
            return "(" + claim.required().size() + " required, "
                    + claim.optional().size() + " optional, min-present "
                    + claim.minPresent()
                    + (claim.refuseExtras() ? "" : ", extras allowed") + ")";
        }
        return Display.of(form.argument());
    }

    static final class Display {
        private Display() {}

        static String of(Object value) {
            String text = String.valueOf(value);
            return text.length() <= 60 ? text : text.substring(0, 57) + "...";
        }
    }
}
