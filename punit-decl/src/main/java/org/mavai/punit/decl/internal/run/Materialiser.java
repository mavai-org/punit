package org.mavai.punit.decl.internal.run;

import java.util.List;
import java.util.Map;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.CriterionDeclaration;
import org.mavai.punit.decl.internal.model.FormDeclaration;
import org.mavai.punit.decl.internal.model.SetOfDeclaration;

/**
 * Graduation: emit the equivalent contract as Java source the developer
 * owns. The emitted class is the contract the contract file
 * instantiates — the same criteria, thresholds, and declaration order —
 * expressed directly against punit's authoring surface. One-shot
 * scaffolding: after materialising, the source is the developer's;
 * nothing round-trips. Graduation transfers ownership deliberately:
 * the invocation is a stub (the binding was the reader's), and
 * path-qualified checks carry a TODO directing the developer to their
 * own selection library (the JSONPath/XPath engines are private to the
 * reader).
 */
public final class Materialiser {

    private Materialiser() {}

    public static String materialise(ContractDeclaration declaration) {
        String className = typeName(declaration.contract());
        StringBuilder out = new StringBuilder();
        out.append("// Materialised from the contract file declaring '")
                .append(declaration.contract()).append("'.\n")
                .append("// This is now your code. The contract file instantiated exactly\n")
                .append("// this contract; edit it freely — the declarative surface is no\n")
                .append("// longer involved.\n\n");
        out.append("import static org.mavai.punit.api.criterion.Criteria.*;\n\n");
        out.append("import org.mavai.outcome.Outcome;\n");
        out.append("import org.mavai.punit.api.NoFactors;\n");
        out.append("import org.mavai.punit.api.ServiceContract;\n");
        out.append("import org.mavai.punit.api.TokenTracker;\n");
        out.append("import org.mavai.punit.api.criterion.Criteria;\n\n");
        out.append("public final class ").append(className)
                .append(" implements ServiceContract<NoFactors, String, String> {\n\n");
        out.append("    @Override\n    public String id() {\n        return \"")
                .append(declaration.contract()).append("\";\n    }\n\n");
        out.append("    @Override\n")
                .append("    public Outcome<String> invoke(String input, TokenTracker tracker) {\n")
                .append("        // TODO: your service call — the contract file used the "
                        + "binding '")
                .append(declaration.service()).append("'.\n")
                .append("        throw new UnsupportedOperationException(\"graduate the "
                        + "invocation\");\n    }\n\n");
        out.append("    @Override\n    public Criteria<String> criteria() {\n");
        if (!declaration.transforms().isEmpty()) {
            for (Map.Entry<String, String> view : declaration.transforms().entrySet()) {
                out.append("        // TODO: view '").append(view.getKey())
                        .append("' was the reader's ").append(view.getValue())
                        .append(" transformation — express it with .transforming(...) and\n")
                        .append("        // your own parser; a failed transform returns "
                                + "Outcome.fail.\n");
            }
        }
        List<CriterionDeclaration> criteria = declaration.criteria();
        if (criteria.size() == 1) {
            out.append("        return ").append(criterionSource(criteria.get(0), "                "))
                    .append(";\n");
        } else {
            out.append("        return Criteria.<String>of(\n");
            for (int i = 0; i < criteria.size(); i++) {
                out.append("                ")
                        .append(criterionSource(criteria.get(i), "                        "));
                out.append(i < criteria.size() - 1 ? ",\n" : "\n");
            }
            out.append("        );\n");
        }
        out.append("    }\n}\n");
        return out.toString();
    }

    private static String criterionSource(CriterionDeclaration criterion, String indent) {
        StringBuilder out = new StringBuilder();
        // The explicit witness pins the chain's subject type — chained
        // generic calls are not poly expressions, so inference cannot
        // reach back from the enclosing of(...).
        out.append(criterion.threshold() != null
                ? "meeting().<String>passRate(" + criterion.threshold() + ")"
                : "empirical().<String>passRate()");
        if (criterion.name() != null) {
            out.append("\n").append(indent).append(".name(\"")
                    .append(escape(criterion.name())).append("\")");
        }
        for (FormDeclaration form : criterion.forms()) {
            out.append("\n").append(indent).append(formSource(form, indent));
        }
        if (criterion.optionalSlack() != null) {
            var slack = criterion.optionalSlack();
            out.append("\n").append(indent).append(".optionalSlack(")
                    .append(slack.count() != null
                            ? String.valueOf(slack.count())
                            : "\"" + slack.display() + "\"")
                    .append(")");
        }
        return out.toString();
    }

    private static String formSource(FormDeclaration form, String indent) {
        String key = form.form().key();
        String base;
        if (form.form() == org.mavai.punit.decl.internal.model.PostconditionForm.PARSES) {
            base = "// TODO: `parses: " + form.argument() + "` — forcing your view's "
                    + "computation is the check";
            return base;
        } else if (form.form() == org.mavai.punit.decl.internal.model.PostconditionForm.SATISFIES) {
            base = ".satisfies(\"" + escape(String.valueOf(form.argument()))
                    + "\", response -> Outcome.ok()) // TODO: inline your registered "
                    + "'" + form.argument() + "' predicate";
        } else if (form.argument() instanceof SetOfDeclaration claim) {
            base = ".satisfies(\"" + key + "\", response -> Outcome.ok()) // TODO: the "
                    + "graded set claim (" + claim.required().size() + " required, "
                    + claim.optional().size() + " optional, min-present "
                    + claim.minPresent() + ") — judge the selection with your own "
                    + "collection code";
        } else {
            base = ".satisfies(\"" + key + " " + escape(display(form.argument()))
                    + "\", response -> Outcome.ok()) // TODO: judge `" + key + ": "
                    + display(form.argument()) + "`";
        }
        if (form.optional()) {
            base += "\n" + indent + ".optional()";
        }
        if (form.path() != null) {
            base = ".satisfies(\"" + key + " at " + escape(form.path())
                    + "\", response -> Outcome.ok()) // TODO: `path: " + form.path()
                    + "` in view '" + form.view() + "' — select with your JSONPath/XPath "
                    + "library of choice, then judge `" + key + ": "
                    + display(form.argument()) + "`"
                    + (form.optional() ? "\n" + indent + ".optional()" : "");
        }
        return base;
    }

    private static String display(Object argument) {
        String text = String.valueOf(argument);
        return text.length() <= 40 ? text : text.substring(0, 37) + "...";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** The contract id as a Java type name: kebab-case to UpperCamel + suffix. */
    public static String typeName(String contractId) {
        StringBuilder name = new StringBuilder();
        boolean upper = true;
        for (char c : contractId.toCharArray()) {
            if (c == '-' || c == '_' || c == '.') {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        if (name.length() == 0) {
            name.append("Materialised");
        }
        if (Character.isDigit(name.charAt(0))) {
            name.insert(0, "Contract");
        }
        return name.append("ServiceContract").toString();
    }
}
