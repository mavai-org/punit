package org.mavai.punit.decl.internal.run;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.DeclaredIntent;
import org.mavai.punit.decl.internal.model.InputDeclaration;
import org.mavai.punit.runtime.PUnit.Declared;
import org.mavai.punit.runtime.PUnit;

/**
 * One declarative run: resolves the contract by its declared name,
 * compiles it onto punit's existing machinery, and drives the engine
 * through the same {@code PUnit.testing(...)} path a builder author
 * uses — a front-end, never a second engine.
 */
public final class DeclaredRun implements Declared {

    private final Class<?> caller;
    private final String methodName;
    private final String explicitName;

    private Integer samples;
    private Class<?> bindingsClass;
    private java.nio.file.Path servicesFile;
    private Double power;
    private Double tolerateBare;
    private final java.util.Map<String, Double> tolerateNamed = new java.util.LinkedHashMap<>();

    public DeclaredRun(Class<?> caller, String methodName, String explicitName) {
        this.caller = caller;
        this.methodName = methodName;
        this.explicitName = explicitName;
    }

    @Override
    public Declared samples(int samples) {
        this.samples = samples;
        return this;
    }

    @Override
    public Declared bindings(Class<?> bindingsClass) {
        this.bindingsClass = bindingsClass;
        return this;
    }

    @Override
    public Declared services(java.nio.file.Path servicesFile) {
        this.servicesFile = servicesFile;
        return this;
    }

    @Override
    public Declared atPower(double power) {
        this.power = power;
        return this;
    }

    @Override
    public Declared tolerating(double rate) {
        this.tolerateBare = rate;
        return this;
    }

    @Override
    public Declared tolerating(String criterion, double rate) {
        this.tolerateNamed.put(criterion, rate);
        return this;
    }

    @Override
    public void assertPasses() {
        Instantiated instantiated = instantiate(true);
        Sizing.Sized sized = Sizing.resolve(instantiated.declaration(), samples);
        runPlan(instantiated.declaration(), sized);
        PUnit.testing(instantiated.sampling(sized.samples()))
                .intent(instantiated.declaration().intent() == DeclaredIntent.SMOKE
                        ? TestIntent.SMOKE
                        : TestIntent.VERIFICATION)
                .assertPasses();
    }

    @Override
    public void run() {
        Instantiated instantiated = instantiate(false);
        Sizing.Sized sized = measureBudget(instantiated.declaration());
        runPlan(instantiated.declaration(), sized);
        PUnit.measuring(instantiated.sampling(sized.samples())).run();
    }

    @Override
    public void assertMeets() {
        Instantiated instantiated = instantiate(false);
        Sizing.Sized sized = measureBudget(instantiated.declaration());
        runPlan(instantiated.declaration(), sized);
        PUnit.measuring(instantiated.sampling(sized.samples())).assertMeets();
    }

    /**
     * A measurement's budget is always typed: property, then the
     * builder's {@code samples(N)} — never a default.
     */
    private Sizing.Sized measureBudget(ContractDeclaration declaration) {
        if (power != null || tolerateBare != null || !tolerateNamed.isEmpty()) {
            throw new ContractConfigurationException(
                    "tolerate/power overrides target the test posture — a measure run records "
                            + "every criterion and consults no claims");
        }
        String property = "punit.samples." + declaration.contract();
        String propertyValue = System.getProperty(property);
        if (propertyValue != null) {
            return new Sizing.Sized(Integer.parseInt(propertyValue.trim()), "set via -D" + property);
        }
        if (samples != null) {
            return new Sizing.Sized(samples, "set via .samples(" + samples + ")");
        }
        throw new ContractConfigurationException(
                "a measurement's budget is an experimental-design decision — type it with "
                        + ".samples(N) or -D" + property + "=N (1,000 is baseline-grade; a "
                        + "smaller deliberate budget is legitimate — an empirical bar derived "
                        + "from a smaller baseline widens honestly)");
    }

    private void runPlan(ContractDeclaration declaration, Sizing.Sized sized) {
        System.out.println("[PUNIT] run plan: contract '" + declaration.contract() + "', "
                + sized.samples() + " samples — " + sized.provenance());
    }

    private record Instantiated(
            ContractDeclaration declaration,
            ServiceContract<NoFactors, Object, String> contract,
            List<Object> inputs) {

        Sampling<NoFactors, Object, String> sampling(int samples) {
            return Sampling.<NoFactors, Object, String>builder()
                    .serviceContractFactory(factors -> contract)
                    .inputs(inputs)
                    .samples(samples)
                    .build();
        }
    }

    private Instantiated instantiate(boolean testPosture) {
        ContractLocator.Located located = ContractLocator.locate(caller, methodName, explicitName);
        ContractDeclaration declaration = located.declaration();
        if (declaration.latency() != null && !declaration.latency().empirical().isEmpty()) {
            throw new ContractConfigurationException(
                    "the empirical `latency:` shape arrives with a later phase of the "
                            + "declarative surface — declare explicit ceilings meanwhile");
        }
        RiskClaims claims = testPosture
                ? RiskClaims.resolve(declaration, samples, power, tolerateBare, tolerateNamed)
                : RiskClaims.none();
        BindingsRegistry registry = BindingsRegistry.of(resolveBindingsClass());
        ServicesResolver services = ServicesResolver.resolve(caller, servicesFile, registry);
        org.mavai.punit.decl.spi.ConfiguredService defined = services.lookup(declaration.service());
        final BindingsRegistry.Invoker invoker;
        Map<String, String> configurationCovariates;
        if (defined != null) {
            // A definition wins service: resolution over a same-named binding.
            invoker = defined::invoke;
            configurationCovariates = defined.configurationCovariates();
        } else if (registry.hasBinding(declaration.service())) {
            invoker = registry.resolveBinding(declaration.service());
            configurationCovariates = Map.of();
        } else {
            // Neither population resolves the name — the binding
            // registry's refusal names the known bindings; extend it
            // with the definition population.
            try {
                registry.resolveBinding(declaration.service());
                throw new IllegalStateException("unreachable");
            } catch (ContractConfigurationException refusal) {
                throw new ContractConfigurationException(refusal.getMessage()
                        + "; no mavai-services.yaml definition names it either "
                        + "(registered types: " + services.registeredTypeNames() + ")");
            }
        }
        Map<String, String> covariateFeed = registry.covariateFeed(declaration.service());
        for (String key : covariateFeed.keySet()) {
            if (configurationCovariates.containsKey(key)) {
                throw new ContractConfigurationException(
                        "covariate '" + key + "' arrives from both the resolved configuration "
                                + "and the @Covariates feed for service '" + declaration.service()
                                + "' — one identity, two feeds, no overlapping keys");
            }
        }
        Map<String, String> covariates = new java.util.LinkedHashMap<>(configurationCovariates);
        covariates.putAll(covariateFeed);
        StockViews views = new StockViews(declaration, registry);
        AtomicReference<Object> currentInput = new AtomicReference<>();
        CriteriaCompiler compiler = new CriteriaCompiler(
                declaration, views, currentInput, claims.tolerateByCriterion(), claims.power(),
                registry);
        compiler.validateEagerly();
        Criteria<String> criteria = compiler.compile();
        ServiceContract<NoFactors, Object, String> contract = new ServiceContract<>() {
            @Override
            public Criteria<String> criteria() {
                return criteria;
            }

            @Override
            public Outcome<String> invoke(Object input, TokenTracker tracker) {
                currentInput.set(input);
                return invoker.invoke(input);
            }

            @Override
            public String id() {
                return declaration.contract();
            }

            @Override
            public List<org.mavai.punit.api.covariate.Covariate> covariates() {
                return covariates.keySet().stream()
                        .map(key -> org.mavai.punit.api.covariate.Covariate.custom(key,
                                org.mavai.punit.api.covariate.CovariateCategory.CONFIGURATION))
                        .toList();
            }

            @Override
            public Map<String, java.util.function.Supplier<String>> customCovariateResolvers() {
                Map<String, java.util.function.Supplier<String>> resolvers =
                        new java.util.LinkedHashMap<>();
                covariates.forEach((key, value) -> resolvers.put(key, () -> value));
                return resolvers;
            }

            @Override
            public org.mavai.punit.api.criterion.LatencyCriterion latency() {
                if (declaration.latency() == null || declaration.latency().ceilings().isEmpty()) {
                    return ServiceContract.super.latency();
                }
                org.mavai.punit.api.criterion.LatencyCriterion criterion = null;
                for (var ceiling : declaration.latency().ceilings()) {
                    org.mavai.punit.api.PercentileKey key =
                            org.mavai.punit.api.PercentileKey.valueOf(
                                    ceiling.percentile().toUpperCase(java.util.Locale.ROOT));
                    java.time.Duration bound = java.time.Duration.ofMillis(ceiling.millis());
                    criterion = criterion == null
                            ? Criteria.meeting().atMost(key, bound)
                            : criterion.atMost(key, bound);
                }
                return criterion;
            }
        };

        List<Object> inputs = declaration.inputs().stream()
                .map(InputDeclaration::value)
                .toList();
        return new Instantiated(declaration, contract, inputs);
    }

    private Class<?> resolveBindingsClass() {
        if (bindingsClass != null) {
            return bindingsClass;
        }
        String conventional = caller.getPackageName() + ".MavaiBindings";
        try {
            return Class.forName(conventional, false, caller.getClassLoader());
        } catch (ClassNotFoundException absent) {
            throw new ContractConfigurationException(
                    "no bindings class: define " + conventional + " (the conventional name) or "
                            + "pass one explicitly with .bindings(YourBindings.class)", absent);
        }
    }
}
