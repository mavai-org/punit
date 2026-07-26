package org.mavai.punit.decl.internal.run;

import org.mavai.punit.decl.Binding;

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
}
