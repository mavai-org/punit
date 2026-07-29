package org.mavai.punit.decl.internal.parser;

import static org.mavai.punit.decl.internal.parser.Yaml.fail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mavai.punit.decl.internal.model.SetElements;
import org.mavai.punit.decl.internal.model.SetOfDeclaration;

/**
 * The {@code set-of:} operand — the composite graded set claim —
 * normalised at parse under membership semantics: a set is a set.
 * Duplicates in a declared list collapse to one entry with a console
 * warning (most likely an authoring typo), never a refusal; every
 * contradiction and unsatisfiable or saturated floor refuses at load;
 * a spelling a sharper form owns is refused naming that form.
 */
final class SetOfParser {

    private static final List<String> KEYS =
            List.of("required", "optional", "min-present", "refuse-extras");
    private static final Pattern PERCENT = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)%$");

    private SetOfParser() {}

    static SetOfDeclaration parse(Object argument, String where) {
        if (!(argument instanceof Map<?, ?> mapping)) {
            throw fail(where + ": `set-of:` takes a mapping — required:/optional: member "
                    + "lists, min-present:, refuse-extras:");
        }
        for (Object key : mapping.keySet()) {
            if (!KEYS.contains(key)) {
                throw fail(where + ": `set-of:` has unknown key `" + key + ":` — its keys "
                        + "are " + String.join(", ", KEYS));
            }
        }
        List<Object> required = memberList(mapping, "required", where);
        List<Object> optional = memberList(mapping, "optional", where);
        if (required.isEmpty() && optional.isEmpty()) {
            throw fail(where + ": `set-of:` states nothing — declare `required:` and/or "
                    + "`optional:` members");
        }
        Object refuseExtrasValue = mapping.containsKey("refuse-extras")
                ? mapping.get("refuse-extras")
                : Boolean.TRUE;
        if (!(refuseExtrasValue instanceof Boolean refuseExtras)) {
            throw fail(where + ": `refuse-extras:` takes a boolean");
        }
        if (optional.isEmpty()) {
            // A sharper name for the stated claim exists — refuse and name it.
            String sharper = refuseExtras ? "equals-set" : "contains-set";
            throw fail(where + ": a `set-of:` without `optional:` members states `"
                    + sharper + ":` — say that");
        }
        int minPresent = minPresent(mapping.get("min-present"), optional.size(), where);
        if (minPresent > optional.size()) {
            throw fail(where + ": `min-present: " + mapping.get("min-present") + "` exceeds "
                    + "the `optional:` list's distinct size (" + optional.size() + ")");
        }
        if (minPresent == optional.size()) {
            throw fail(where + ": `min-present: " + mapping.get("min-present") + "` equals "
                    + "the `optional:` list's distinct size (" + optional.size() + ") — "
                    + "every optional member is required; move them to `required:`");
        }
        Set<Object> optionalKeys = new HashSet<>();
        for (Object member : optional) {
            optionalKeys.add(SetElements.key(member));
        }
        for (Object member : required) {
            if (optionalKeys.contains(SetElements.key(member))) {
                throw fail(where + ": " + Yaml.display(member) + " appears in both "
                        + "`required:` and `optional:` — a member is one or the other");
            }
        }
        return new SetOfDeclaration(required, optional, minPresent, refuseExtras);
    }

    /**
     * One declared member list as a set: order kept, duplicates
     * collapsed with a console warning.
     */
    private static List<Object> memberList(Map<?, ?> mapping, String name, String where) {
        Object value = mapping.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> items) || items.isEmpty()
                || !items.stream().allMatch(SetOfParser::scalarElement)) {
            throw fail(where + ": `set-of:` `" + name + ":` takes a non-empty list of "
                    + "scalar values");
        }
        List<Object> members = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (Object item : items) {
            Object key = SetElements.key(item);
            if (seen.contains(key)) {
                System.err.println("warning: " + where + ": `set-of:` `" + name + ":` lists "
                        + Yaml.display(item) + " more than once — duplicates collapse to "
                        + "one entry (likely a typo)");
            } else {
                seen.add(key);
                members.add(item);
            }
        }
        return members;
    }

    /**
     * The floor over the optional list: a distinct-member count, or an
     * explicit percentage resolved by floor (the {@code %} suffix is
     * the disambiguator, exactly as {@code optional-slack:}).
     */
    private static int minPresent(Object value, int optionalSize, String where) {
        if (value == null) {
            return 0;
        }
        boolean nonNegativeInteger = !(value instanceof Boolean)
                && value instanceof Number count
                && count.longValue() >= 0
                && !(value instanceof Double || value instanceof Float);
        if (nonNegativeInteger) {
            return ((Number) value).intValue();
        }
        if (value instanceof String spelling) {
            Matcher matcher = PERCENT.matcher(spelling.strip());
            if (matcher.matches()) {
                return new BigDecimal(matcher.group(1))
                        .multiply(BigDecimal.valueOf(optionalSize))
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                        .intValue();
            }
        }
        throw fail(where + ": `min-present:` takes a non-negative whole count of distinct "
                + "optional members, or an explicit percentage like `80%` — got "
                + Yaml.display(value) + " (a bare fraction is never guessed at)");
    }

    private static boolean scalarElement(Object element) {
        return element == null
                || element instanceof String
                || element instanceof Number
                || element instanceof Boolean;
    }
}
