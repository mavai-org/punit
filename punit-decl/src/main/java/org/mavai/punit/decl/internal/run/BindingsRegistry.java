package org.mavai.punit.decl.internal.run;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.mavai.outcome.Outcome;
import org.mavai.punit.decl.Binding;
import org.mavai.punit.decl.BindingFactory;
import org.mavai.punit.decl.Check;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.Covariates;
import org.mavai.punit.decl.Scorer;
import org.mavai.punit.decl.Stepper;
import org.mavai.punit.decl.Transform;
import org.mavai.punit.decl.internal.model.FormDeclaration;

/**
 * The bindings artefact's registries: {@link Binding @Binding} service
 * bindings, {@link BindingFactory @BindingFactory} configurable service
 * types, {@link Transform @Transform} named transformations,
 * {@link Check @Check} named checks, and
 * {@link Covariates @Covariates} computed covariate feeds — each
 * resolved by name at load time with fail-fast duplicate and
 * unresolvable semantics.
 */
final class BindingsRegistry {

    private final Class<?> bindingsClass;
    private final Object instance;
    private final Map<String, Method> bindings = new LinkedHashMap<>();
    private final Map<String, Method> factories = new LinkedHashMap<>();
    private final Map<String, Method> transforms = new LinkedHashMap<>();
    private final Map<String, Method> checks = new LinkedHashMap<>();
    private final Map<String, Method> covariateFeeds = new LinkedHashMap<>();
    private final Map<String, Method> steppers = new LinkedHashMap<>();
    private final Map<String, Method> scorers = new LinkedHashMap<>();

    private BindingsRegistry(Class<?> bindingsClass, Object instance) {
        this.bindingsClass = bindingsClass;
        this.instance = instance;
    }

    static BindingsRegistry of(Class<?> bindingsClass) {
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
        BindingsRegistry registry = new BindingsRegistry(bindingsClass, instance);
        for (Method method : bindingsClass.getDeclaredMethods()) {
            registry.register(method, Binding.class, Binding::value, registry.bindings, "binding");
            registry.register(method, BindingFactory.class, BindingFactory::value,
                    registry.factories, "service type");
            registry.register(method, Transform.class, Transform::value,
                    registry.transforms, "transformation");
            registry.register(method, Check.class, Check::value, registry.checks, "check");
            registry.register(method, Covariates.class, Covariates::value,
                    registry.covariateFeeds, "covariate feed");
            registry.register(method, Stepper.class, Stepper::value, registry.steppers, "stepper");
            registry.register(method, Scorer.class, Scorer::value, registry.scorers, "scorer");
        }
        for (String view : registry.transforms.keySet()) {
            if (view.equals(FormDeclaration.RAW_VIEW)) {
                throw new ContractConfigurationException(
                        "@Transform(\"raw\") in " + bindingsClass.getSimpleName() + " — `raw` is "
                                + "the reserved name of the untransformed response");
            }
        }
        return registry;
    }

    private <A extends Annotation> void register(Method method, Class<A> annotationType,
            Function<A, String> nameOf, Map<String, Method> registry, String what) {
        A annotation = method.getAnnotation(annotationType);
        if (annotation == null) {
            return;
        }
        String name = nameOf.apply(annotation);
        if (name.isEmpty()) {
            throw new ContractConfigurationException(
                    "@" + annotationType.getSimpleName() + " on " + bindingsClass.getSimpleName()
                            + "." + method.getName() + " needs a non-empty name");
        }
        Method previous = registry.putIfAbsent(name, method);
        if (previous != null) {
            throw new ContractConfigurationException(
                    what + " '" + name + "' is registered twice in "
                            + bindingsClass.getSimpleName() + " (" + previous.getName()
                            + " and " + method.getName() + ") — names are unique per registry");
        }
        method.setAccessible(true);
    }

    Class<?> bindingsClass() {
        return bindingsClass;
    }

    Map<String, Method> factories() {
        return factories;
    }

    boolean hasBinding(String serviceName) {
        return bindings.containsKey(serviceName);
    }

    boolean hasTransform(String name) {
        return transforms.containsKey(name);
    }

    /** The resolved invoker for a bare binding, or a refusal naming the known ones. */
    Invoker resolveBinding(String serviceName) {
        Method method = bindings.get(serviceName);
        if (method == null) {
            String known = bindings.isEmpty() ? "none registered" : String.join(", ", bindings.keySet());
            throw new ContractConfigurationException(
                    "service '" + serviceName + "' is not bound; known bindings in "
                            + bindingsClass.getSimpleName() + ": " + known
                            + " (register with @Binding(\"" + serviceName + "\"))");
        }
        return input -> asResponse(method, invoke(method, arguments(method, input)));
    }

    /** The named transformation, applied: the view's value or a transform failure. */
    Outcome<Object> applyTransform(String name, String response) {
        Method method = transforms.get(name);
        Object result = invoke(method, new Object[] {response});
        if (result instanceof Outcome.Fail<?> fail) {
            @SuppressWarnings("unchecked")
            Outcome<Object> failure = (Outcome<Object>) fail;
            return failure;
        }
        if (result instanceof Outcome.Ok<?> ok) {
            return Outcome.ok(ok.value());
        }
        return Outcome.ok(result);
    }

    /** The named check, applied to its subject: ok, or the check's failure. */
    Outcome<?> applyCheck(String name, Object subject) {
        Method method = checks.get(name);
        if (method == null) {
            String known = checks.isEmpty() ? "none registered" : String.join(", ", checks.keySet());
            throw new ContractConfigurationException(
                    "`satisfies: " + name + "` names no registered check; known checks in "
                            + bindingsClass.getSimpleName() + ": " + known
                            + " (register with @Check(\"" + name + "\"))");
        }
        Object result = invoke(method, new Object[] {subject});
        if (result instanceof Outcome<?> outcome) {
            return outcome;
        }
        if (result instanceof Boolean holds) {
            return holds ? Outcome.ok() : Outcome.fail("check", "check '" + name + "' did not hold");
        }
        throw new ContractConfigurationException(
                "check '" + name + "' must answer with a boolean or an Outcome, got "
                        + (result == null ? "null" : result.getClass().getSimpleName()));
    }

    boolean hasCheck(String name) {
        return checks.containsKey(name);
    }

    /** The service's computed covariate feed, invoked once; empty when none registered. */
    Map<String, String> covariateFeed(String serviceName) {
        Method method = covariateFeeds.get(serviceName);
        if (method == null) {
            return Map.of();
        }
        Object result = invoke(method, new Object[0]);
        if (!(result instanceof Map<?, ?> feed)) {
            throw new ContractConfigurationException(
                    "@Covariates(\"" + serviceName + "\") must return Map<String, String>, got "
                            + (result == null ? "null" : result.getClass().getSimpleName()));
        }
        Map<String, String> covariates = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : feed.entrySet()) {
            covariates.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return covariates;
    }

    /** Whether the bindings class registers a stepper of the name. */
    boolean hasStepper(String name) {
        return steppers.containsKey(name);
    }

    /**
     * The named stepper's factory method, or a refusal naming the
     * registered ones alongside the given built-in names.
     */
    Method stepperFactory(String name, java.util.Set<String> builtInNames) {
        Method method = steppers.get(name);
        if (method == null) {
            java.util.List<String> known = new java.util.ArrayList<>();
            builtInNames.stream().sorted()
                    .forEach(builtIn -> known.add(builtIn + " (built in)"));
            known.addAll(steppers.keySet());
            throw new ContractConfigurationException(
                    "`stepper: " + name + "` names no registered stepper; known steppers in "
                            + bindingsClass.getSimpleName() + ": "
                            + (known.isEmpty() ? "none registered" : String.join(", ", known))
                            + " (register with @Stepper(\"" + name + "\"))");
        }
        return method;
    }

    /** The named scorer, resolved — {@code pass-rate} is built in. */
    org.mavai.punit.api.spec.Scorer resolveScorer(String name) {
        if (name.equals("pass-rate")) {
            return org.mavai.punit.api.spec.Scorer.observedPassRate();
        }
        Method method = scorers.get(name);
        if (method == null) {
            String known = scorers.isEmpty() ? "pass-rate (built in)"
                    : "pass-rate (built in), " + String.join(", ", scorers.keySet());
            throw new ContractConfigurationException(
                    "`scorer: " + name + "` names no registered scorer; known scorers: " + known
                            + " (register with @Scorer(\"" + name + "\"))");
        }
        Object result = invoke(method, new Object[0]);
        if (!(result instanceof org.mavai.punit.api.spec.Scorer scorer)) {
            throw new ContractConfigurationException(
                    "@Scorer(\"" + name + "\") must return an org.mavai.punit.api.spec.Scorer, got "
                            + (result == null ? "null" : result.getClass().getSimpleName()));
        }
        return scorer;
    }

    interface Invoker {
        Outcome<String> invoke(Object input);
    }

    /** Adapts a factory-produced per-sample callable to the invoker seam. */
    Invoker adaptCallable(String serviceName, Object callable) {
        if (!(callable instanceof Function<?, ?>)) {
            throw new ContractConfigurationException(
                    "the factory for service '" + serviceName + "' must return a "
                            + "java.util.function.Function (the per-sample callable), got "
                            + (callable == null ? "null" : callable.getClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        Function<Object, Object> function = (Function<Object, Object>) callable;
        return input -> asResponse(null, function.apply(input));
    }

    Object invoke(Method method, Object[] arguments) {
        try {
            return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : instance, arguments);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("bindings invocation failed: " + error, error);
        } catch (IllegalArgumentException error) {
            throw new ContractConfigurationException(
                    method.getName() + " cannot accept the given arguments: " + error.getMessage(),
                    error);
        } catch (InvocationTargetException error) {
            // A defect inside the bindings class aborts the run — the
            // family's expected-failure-versus-defect discipline.
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException("bindings defect: " + cause, cause);
        }
    }

    private Object[] arguments(Method method, Object input) {
        int parameterCount = method.getParameterCount();
        if (input instanceof List<?> tuple && parameterCount == tuple.size() && parameterCount != 1) {
            return tuple.toArray();
        }
        if (parameterCount == 1) {
            return new Object[] {input};
        }
        throw new ContractConfigurationException(
                "binding '" + method.getName() + "' takes " + parameterCount
                        + " parameters but the input " + display(input) + " does not fit — "
                        + "an argument-list input must match the binding's arity");
    }

    @SuppressWarnings("unchecked")
    private Outcome<String> asResponse(Method method, Object result) {
        String who = method == null ? "the per-sample callable" : "binding '" + method.getName() + "'";
        if (result instanceof Outcome.Fail<?> fail) {
            return (Outcome<String>) fail;
        }
        if (result instanceof Outcome.Ok<?> ok) {
            if (ok.value() instanceof String) {
                return (Outcome<String>) ok;
            }
            throw new ContractConfigurationException(
                    who + " must carry the service's response as a String, got Outcome of "
                            + (ok.value() == null ? "null" : ok.value().getClass().getSimpleName()));
        }
        if (result instanceof String response) {
            return Outcome.ok(response);
        }
        throw new ContractConfigurationException(
                who + " must return the service's response as a String (or an Outcome), got "
                        + (result == null ? "null" : result.getClass().getSimpleName()));
    }

    private static String display(Object input) {
        String text = String.valueOf(input);
        return text.length() <= 60 ? "'" + text + "'" : "'" + text.substring(0, 57) + "...'";
    }
}
