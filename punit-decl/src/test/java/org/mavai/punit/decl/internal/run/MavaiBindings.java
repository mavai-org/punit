package org.mavai.punit.decl.internal.run;

import java.util.Map;
import java.util.function.Function;
import org.mavai.punit.decl.Binding;
import org.mavai.punit.decl.BindingFactory;
import org.mavai.punit.decl.Check;
import org.mavai.punit.decl.Covariates;
import org.mavai.punit.decl.Transform;

/**
 * The conventional bindings class for this package's declarative-run
 * tests — discovered by name, exercising the zero-configuration route.
 */
class MavaiBindings {

    @Binding("greeting-service")
    String greet(String name) {
        return "hello " + name + "!";
    }

    @Binding("rude-service")
    String dismiss(String name) {
        return "go away, " + name;
    }

    @Binding("fortune-teller")
    String fortune(String name) {
        return "a great fortune awaits " + name + ".";
    }

    @Binding("basket-builder")
    String basket(String instruction) {
        if (instruction.contains("eggs")) {
            return "{\"items\": [{\"name\": \"egg\", \"quantity\": 12}]}";
        }
        return "{\"items\": [{\"name\": \"milk\", \"quantity\": 2}]}";
    }

    @Binding("broken-json")
    String brokenJson(String instruction) {
        return "this is not json {";
    }

    @Binding("repeater")
    String repeat(String item, int count) {
        return item + " x" + count;
    }

    @Binding("receipt-service")
    String receipt(String order) {
        return "<receipt><total>12.50</total></receipt>";
    }

    @Binding("mostly-polite")
    String mostlyPolite(String name) {
        // Deterministically imperfect: every twentieth response is curt,
        // so a measured baseline records a 0.95 rate — inside (0, 1),
        // as the engine's risk-driven sizing requires.
        int turn = MOSTLY_POLITE_TURN++;
        return turn % 20 == 19 ? "hmpf, " + name : "hello " + name + "!";
    }

    private static int MOSTLY_POLITE_TURN;

    @Binding("dual-source")
    String dualSourceBinding(String input) {
        return "from-the-binding";
    }

    @BindingFactory("triage")
    Function<String, String> triage(String tone, double certainty) {
        return request -> "category: billing (" + tone + " at " + certainty + ")";
    }

    @Transform("basket-judge")
    Map<String, Object> judge(String response) {
        return Map.of("namesUnique", "true");
    }

    @Transform("receipt-dom")
    org.w3c.dom.Document receiptDom(String response) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(response)));
    }

    @Check("mentions-category")
    boolean mentionsCategory(String subject) {
        return subject.contains("category");
    }

    @Covariates("triage-assistant")
    Map<String, String> triageCovariates() {
        return Map.of("rules-hash", "abc123");
    }

    @org.mavai.punit.decl.Stepper("certainty-stepper")
    org.mavai.punit.api.spec.FactorsStepper<Map<String, Object>> certaintyStepper(
            double step, double stop) {
        return (current, history) -> {
            double certainty = ((Number) current.get("certainty")).doubleValue() + step;
            if (certainty > stop) {
                return org.mavai.punit.api.spec.NextFactor.stop();
            }
            Map<String, Object> next = new java.util.LinkedHashMap<>(current);
            next.put("certainty", certainty);
            return org.mavai.punit.api.spec.NextFactor.next(next);
        };
    }
}
