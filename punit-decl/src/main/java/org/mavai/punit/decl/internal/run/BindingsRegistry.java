package org.mavai.punit.decl.internal.run;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.Binding;
import org.mavai.punit.decl.ContractConfigurationException;

/**
 * The bindings-class registry: {@link Binding @Binding}-annotated
 * methods, resolved by name at load time with fail-fast duplicate and
 * unresolvable semantics. Discovery is the conventional
 * {@code MavaiBindings} class in the test's package, or the
 * compile-checked {@code .bindings(Class)} override.
 */
final class BindingsRegistry {

    private final Class<?> bindingsClass;
    private final Object instance;
    private final Map<String, Method> bindings;

    private BindingsRegistry(Class<?> bindingsClass, Object instance, Map<String, Method> bindings) {
        this.bindingsClass = bindingsClass;
        this.instance = instance;
        this.bindings = bindings;
    }

    static BindingsRegistry of(Class<?> bindingsClass) {
        Map<String, Method> bindings = new LinkedHashMap<>();
        for (Method method : bindingsClass.getDeclaredMethods()) {
            Binding annotation = method.getAnnotation(Binding.class);
            if (annotation == null) {
                continue;
            }
            String name = annotation.value();
            if (name.isEmpty()) {
                throw new ContractConfigurationException(
                        "@Binding on " + bindingsClass.getSimpleName() + "." + method.getName()
                                + " needs a non-empty name");
            }
            Method previous = bindings.putIfAbsent(name, method);
            if (previous != null) {
                throw new ContractConfigurationException(
                        "binding '" + name + "' is registered twice in "
                                + bindingsClass.getSimpleName() + " (" + previous.getName()
                                + " and " + method.getName() + ") — binding names are unique");
            }
            method.setAccessible(true);
        }
        Object instance;
        try {
            var constructor = bindingsClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
        } catch (ReflectiveOperationException error) {
            throw new ContractConfigurationException(
                    "cannot instantiate bindings class " + bindingsClass.getName()
                            + " — it needs an accessible no-argument constructor", error);
        }
        return new BindingsRegistry(bindingsClass, instance, bindings);
    }

    /** The resolved invoker for a service name, or a refusal naming the known bindings. */
    Invoker resolve(String serviceName) {
        Method method = bindings.get(serviceName);
        if (method == null) {
            String known = bindings.isEmpty() ? "none registered" : String.join(", ", bindings.keySet());
            throw new ContractConfigurationException(
                    "service '" + serviceName + "' is not bound; known bindings in "
                            + bindingsClass.getSimpleName() + ": " + known
                            + " (register with @Binding(\"" + serviceName + "\"))");
        }
        return input -> invoke(method, input);
    }

    interface Invoker {
        Outcome<String> invoke(Object input);
    }

    @SuppressWarnings("unchecked")
    private Outcome<String> invoke(Method method, Object input) {
        Object[] arguments;
        int parameterCount = method.getParameterCount();
        if (input instanceof List<?> tuple && parameterCount == tuple.size() && parameterCount != 1) {
            arguments = tuple.toArray();
        } else if (parameterCount == 1) {
            arguments = new Object[] {input};
        } else {
            throw new ContractConfigurationException(
                    "binding '" + method.getName() + "' takes " + parameterCount
                            + " parameters but the input " + display(input) + " does not fit — "
                            + "an argument-list input must match the binding's arity");
        }
        Object result;
        try {
            result = method.invoke(Modifier.isStatic(method.getModifiers()) ? null : instance, arguments);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("binding invocation failed: " + error, error);
        } catch (IllegalArgumentException error) {
            throw new ContractConfigurationException(
                    "binding '" + method.getName() + "' cannot accept the input "
                            + display(input) + ": " + error.getMessage(), error);
        } catch (InvocationTargetException error) {
            // A defect inside the binding aborts the run — the family's
            // expected-failure-versus-defect discipline: anticipated bad
            // responses return as responses; only bugs throw.
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException("binding defect: " + cause, cause);
        }
        if (result instanceof Outcome.Fail<?> fail) {
            // An anticipated failure travels as data, straight through
            // to the engine's failed-sample accounting.
            return (Outcome<String>) fail;
        }
        if (result instanceof Outcome.Ok<?> ok) {
            if (ok.value() instanceof String) {
                return (Outcome<String>) ok;
            }
            throw new ContractConfigurationException(
                    "binding '" + method.getName() + "' must carry the service's response as a "
                            + "String, got Outcome of "
                            + (ok.value() == null ? "null" : ok.value().getClass().getSimpleName()));
        }
        if (result instanceof String response) {
            return Outcome.ok(response);
        }
        throw new ContractConfigurationException(
                "binding '" + method.getName() + "' must return the service's response as a "
                        + "String (or an Outcome), got "
                        + (result == null ? "null" : result.getClass().getSimpleName()));
    }

    private static String display(Object input) {
        String text = String.valueOf(input);
        return text.length() <= 60 ? "'" + text + "'" : "'" + text.substring(0, 57) + "...'";
    }
}
