package org.mavai.punit.decl.parser;

import static org.mavai.punit.decl.parser.Yaml.fail;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.mavai.punit.decl.model.Form;
import org.mavai.punit.decl.model.FormDeclaration;

/**
 * One postcondition form entry, parsed: the form vocabulary, the
 * {@code in:} view and {@code path:} qualifiers, and the legality
 * checks — only the string forms take a {@code path:}, and
 * {@code parses:} names its view as its argument, never via
 * {@code in:}.
 */
final class FormParser {

    private static final Set<Form> STRING_FORMS =
            Set.of(Form.EQUALS, Form.ONE_OF, Form.CONTAINS, Form.MATCHES);

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
        if (keys.size() != 1) {
            throw fail(where + ": each postcondition declares exactly one form");
        }
        String formKey = keys.iterator().next();
        Form form = forKey(formKey);
        if (form == null) {
            throw fail(where + ": unknown postcondition form `" + formKey + "`");
        }
        Object argument = entry.get(formKey);
        checkArgument(form, argument, where);
        if (path != null) {
            if (!STRING_FORMS.contains(form)) {
                throw fail(where + ": `path:` qualifies the string forms only");
            }
            if (view.equals(FormDeclaration.RAW_VIEW)) {
                throw fail(where + ": `path:` requires `in:` naming a declared view — "
                        + "the raw response is unstructured text");
            }
        }
        if (form == Form.PARSES) {
            if (!view.equals(FormDeclaration.RAW_VIEW)) {
                throw fail(where + ": `parses:` takes no `in:` — it names its view directly");
            }
            if (!(argument instanceof String target) || !views.containsKey(target)) {
                throw fail(where + ": `parses:` references a declared view (declared: "
                        + declared(views) + ")");
            }
        }
        return new FormDeclaration(form, argument, view, path);
    }

    private static void checkArgument(Form form, Object argument, String where) {
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
        }
    }

    private static Form forKey(String key) {
        for (Form form : Form.values()) {
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
