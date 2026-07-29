package org.mavai.punit.decl.internal.parser;

import static org.mavai.punit.decl.internal.parser.Yaml.fail;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.mavai.punit.decl.internal.model.FormDeclaration;
import org.mavai.punit.decl.internal.model.NumericValue;
import org.mavai.punit.decl.internal.model.PostconditionForm;

/**
 * One postcondition form entry, parsed: the form vocabulary, the
 * {@code in:} view and {@code path:} qualifiers, and the legality
 * checks — {@code path:} belongs to the string and value-comparison
 * forms, a set form judges a selection collectively so it requires a
 * declared view and a {@code path:}, and {@code parses:} names its
 * view as its argument, never via {@code in:}.
 */
final class FormParser {

    private FormParser() {}

    static FormDeclaration parse(Map<String, Object> entry, String where, Map<String, String> views) {
        Set<String> keys = new LinkedHashSet<>(entry.keySet());
        String view = FormDeclaration.RAW_VIEW;
        String path = null;
        if (keys.contains("in")) {
            Object viewValue = entry.get("in");
            if (!(viewValue instanceof String name) || name.isEmpty()) {
                throw fail(where + ": `in:` must name a view");
            }
            if (!name.equals(FormDeclaration.RAW_VIEW) && !views.containsKey(name)) {
                throw fail(where + ": `in: " + name + "` names an undeclared view (declared: "
                        + declared(views) + "; `raw` is always available)");
            }
            view = name;
            keys.remove("in");
        }
        if (keys.contains("path")) {
            Object pathValue = entry.get("path");
            if (!(pathValue instanceof String expression) || expression.isEmpty()) {
                throw fail(where + ": `path:` must be a non-empty string");
            }
            path = expression;
            keys.remove("path");
        }
        boolean optional = false;
        if (keys.contains("optional")) {
            if (!Boolean.TRUE.equals(entry.get("optional"))) {
                throw fail(where + ": `optional:` takes the literal `true` and nothing else — "
                        + "required is the default, not a spelling");
            }
            optional = true;
            keys.remove("optional");
        }
        if (keys.size() != 1) {
            throw fail(where + ": each postcondition declares exactly one form");
        }
        String formKey = keys.iterator().next();
        PostconditionForm form = forKey(formKey);
        if (form == null) {
            throw fail(where + ": unknown postcondition form `" + formKey + "`");
        }
        Object argument = entry.get(formKey);
        checkArgument(form, argument, where);
        if (form == PostconditionForm.SET_OF) {
            // The composite operand parses to its normalised declaration:
            // member lists deduplicated (membership semantics), the
            // min-present floor resolved, refuse-extras defaulted.
            argument = SetOfParser.parse(argument, where);
        }
        boolean explicitIn = entry.containsKey("in");
        if (path != null) {
            if (!form.pathCapable()) {
                throw fail(where + ": `path:` qualifies the string and value-comparison forms only");
            }
            if (explicitIn && view.equals(FormDeclaration.RAW_VIEW)) {
                throw fail(where + ": `path:` cannot target `raw` — the raw response is "
                        + "unstructured text; name a declared view with `in:`");
            }
            if (!explicitIn) {
                // The path-conditional default (subject rule, 2026-07-27):
                // a path-bearing check omitting `in:` cannot mean the raw
                // text — a path needs structure — so its subject resolves
                // against the owning criterion (its single `parses:` view,
                // else the contract's sole transform) once all the
                // criterion's forms are known. Unresolved here, by design.
                view = null;
            }
        }
        if (form.collective() && path == null) {
            throw fail(where + ": `" + form.key() + ":` judges the values a path selects, "
                    + "collectively — it requires a `path:` under a declared view "
                    + "(there is no collection over the raw text or a scalar)");
        }
        if (form == PostconditionForm.PARSES) {
            if (optional) {
                throw fail(where + ": `optional:` on `parses:` is refused — a transform "
                        + "failure hard-fails the trial regardless of any optional-slack "
                        + "budget, so the mark would be inert");
            }
            if (!view.equals(FormDeclaration.RAW_VIEW)) {
                throw fail(where + ": `parses:` takes no `in:` — it names its view directly");
            }
            if (!(argument instanceof String target) || !views.containsKey(target)) {
                throw fail(where + ": `parses:` references a declared view (declared: "
                        + declared(views) + ")");
            }
        }
        return new FormDeclaration(form, argument, view, path, optional);
    }

    private static void checkArgument(PostconditionForm form, Object argument, String where) {
        switch (form) {
            case ONE_OF -> {
                if (!(argument instanceof List<?> values)
                        || values.isEmpty()
                        || !values.stream().allMatch(String.class::isInstance)) {
                    throw fail(where + ": `one-of:` takes a non-empty list of strings");
                }
            }
            case EQUALS, CONTAINS, MATCHES -> {
                if (!(argument instanceof String)) {
                    // The guiding refusals (boolean amendment, 2026-07-27):
                    // the intuitive-but-refused spellings name the form that
                    // expresses the intent, instead of stranding the author
                    // at the type rule.
                    if (form == PostconditionForm.EQUALS && argument instanceof Boolean) {
                        throw fail(where + ": `equals:` takes a string — a boolean field is "
                                + "judged with `is: true` / `is: false`");
                    }
                    if (form == PostconditionForm.EQUALS && argument == null) {
                        throw fail(where + ": `equals:` takes a string — a null expectation "
                                + "is `is-null: true`");
                    }
                    throw fail(where + ": `" + form.key() + ":` takes a string");
                }
            }
            case SATISFIES -> {
                if (!(argument instanceof String name) || name.isEmpty()) {
                    throw fail(where + ": `satisfies:` names a check registered in code");
                }
            }
            case PARSES -> {
                // Checked against the declared views below, where the
                // refusal can name them.
            }
            case EQ, NE, LT, LE, GT, GE -> {
                if (!NumericValue.interpretable(argument)) {
                    throw fail(where + ": `" + form.key() + ":` takes a number or a numeric "
                            + "string (sign/decimal/exponent), got "
                            + Yaml.display(argument));
                }
            }
            case NOT_EQUALS, EQUALS_CI -> {
                if (!(argument instanceof String)) {
                    throw fail(where + ": `" + form.key() + ":` takes a string");
                }
            }
            case IS -> {
                if (!(argument instanceof Boolean)) {
                    throw fail(where + ": `is:` takes a boolean — it judges JSON true/false "
                            + "by identity, and the string projections belong to `equals:`; "
                            + "got " + Yaml.display(argument));
                }
            }
            case IS_NULL -> {
                if (!Boolean.TRUE.equals(argument)) {
                    throw fail(where + ": `is-null:` takes the literal `true` and nothing "
                            + "else — the negation is not offered");
                }
            }
            case EQUALS_SET, CONTAINS_SET -> {
                if (!(argument instanceof List<?> values)
                        || values.isEmpty()
                        || !values.stream().allMatch(FormParser::scalarElement)) {
                    throw fail(where + ": `" + form.key() + ":` takes a non-empty list of "
                            + "scalar values (an empty-selection assertion is `count-equals: 0`)");
                }
            }
            case SET_OF -> {
                // Validated and normalised by SetOfParser in parse() —
                // the composite operand owns its own refusal battery.
            }
            case COUNT_EQUALS -> {
                boolean nonNegativeInteger = !(argument instanceof Boolean)
                        && argument instanceof Number count
                        && count.longValue() >= 0
                        && !(argument instanceof Double || argument instanceof Float);
                if (!nonNegativeInteger) {
                    throw fail(where + ": `count-equals:` takes a non-negative integer");
                }
            }
        }
    }

    private static boolean scalarElement(Object element) {
        return element == null
                || element instanceof String
                || element instanceof Number
                || element instanceof Boolean;
    }

    private static PostconditionForm forKey(String key) {
        for (PostconditionForm form : PostconditionForm.values()) {
            if (form.key().equals(key)) {
                return form;
            }
        }
        return null;
    }

    private static String declared(Map<String, String> views) {
        if (views.isEmpty()) {
            return "none declared";
        }
        return views.keySet().stream().sorted().collect(Collectors.joining(", "));
    }
}
