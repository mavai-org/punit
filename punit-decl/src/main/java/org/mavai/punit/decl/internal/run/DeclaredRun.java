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
    private Integer samplesPerConfig;
    private Integer samplesPerIteration;
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
    public Declared samplesPerConfig(int samplesPerConfig) {
        this.samplesPerConfig = samplesPerConfig;
        return this;
    }

    @Override
    public Declared samplesPerIteration(int samplesPerIteration) {
        this.samplesPerIteration = samplesPerIteration;
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
    public void measure() {
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

    @Override
    public void explore() {
        ContractLocator.Located located = ContractLocator.locate(caller, methodName, explicitName);
        ContractDeclaration declaration = located.declaration();
        if (declaration.latency() != null && !declaration.latency().empirical().isEmpty()) {
            throw new ContractConfigurationException(
                    "the empirical `latency:` shape arrives with a later phase of the "
                            + "declarative surface — declare explicit ceilings meanwhile");
        }
        if (samples != null) {
            throw new ContractConfigurationException(
                    ".samples(N) sizes a test or measure run — an exploration is sized per "
                            + "configuration: use .samplesPerConfig(N) (default 5)");
        }
        if (power != null || tolerateBare != null || !tolerateNamed.isEmpty()) {
            throw new ContractConfigurationException(
                    "tolerate/power overrides target the test posture — an explore run is "
                            + "descriptive and consults no claims");
        }
        BindingsRegistry registry = BindingsRegistry.of(resolveBindingsClass());
        ServicesResolver services = ServicesResolver.resolve(
                caller, servicesFile, registry, ServicesResolver.Posture.EXPLORE);
        if (!services.isDefined(declaration.service())) {
            if (registry.hasBinding(declaration.service())) {
                throw new ContractConfigurationException(
                        "service '" + declaration.service() + "' resolves to a bare code "
                                + "binding, which carries no configuration grid — explore "
                                + "needs a mavai-services.yaml definition (a configurable "
                                + "@BindingFactory type with a configuration: block)");
            }
            throw new ContractConfigurationException(
                    "service '" + declaration.service() + "' has no mavai-services.yaml "
                            + "definition to explore (registered types: "
                            + services.registeredTypeNames() + ")");
        }
        Map<String, String> covariateFeed = registry.covariateFeed(declaration.service());
        StockViews views = new StockViews(declaration, registry);
        AtomicReference<Object> currentInput = new AtomicReference<>();
        CriteriaCompiler compiler = new CriteriaCompiler(
                declaration, views, currentInput, Map.of(), null, registry);
        compiler.validateEagerly();
        Criteria<String> criteria = compiler.compile();

        List<Map<String, Object>> grid = services.explorationGrid(declaration.service());
        int perConfig = exploreBudget(declaration);
        List<Object> inputs = declaration.inputs().stream()
                .map(InputDeclaration::value)
                .toList();

        Sampling<Map<String, Object>, Object, String> sampling =
                Sampling.<Map<String, Object>, Object, String>builder()
                        .serviceContractFactory(configuration -> exploreContract(
                                declaration, criteria, currentInput,
                                services, covariateFeed, configuration, inputs, true))
                        .inputs(inputs)
                        .samples(perConfig)
                        .build();

        System.out.println("[PUNIT] run plan: contract '" + declaration.contract() + "', "
                + grid.size() + " configurations x " + perConfig + " samples — explore");

        PUnit.exploring(sampling).grid(grid).run();
    }

    @Override
    public void optimize() {
        optimize(null);
    }

    @Override
    public void optimize(String id) {
        ContractLocator.Located located = ContractLocator.locate(caller, methodName, explicitName);
        ContractDeclaration declaration = located.declaration();
        if (samples != null) {
            throw new ContractConfigurationException(
                    ".samples(N) sizes a test or measure run — an optimization is sized per "
                            + "iteration: use .samplesPerIteration(N) (default 5)");
        }
        if (power != null || tolerateBare != null || !tolerateNamed.isEmpty()) {
            throw new ContractConfigurationException(
                    "tolerate/power overrides target the test posture — an optimize run is "
                            + "descriptive and consults no claims");
        }
        BindingsRegistry registry = BindingsRegistry.of(resolveBindingsClass());
        ServicesResolver services = ServicesResolver.resolve(
                caller, servicesFile, registry, ServicesResolver.Posture.STRICT);
        if (!services.isDefined(declaration.service())) {
            throw new ContractConfigurationException(
                    "service '" + declaration.service() + "' has no mavai-services.yaml "
                            + "definition — an optimize run consumes the definition's "
                            + "`optimizations:` entries");
        }
        List<org.mavai.punit.decl.internal.model.OptimizationDeclaration> entries =
                services.optimizations(declaration.service());
        if (entries.isEmpty()) {
            throw new ContractConfigurationException(
                    "service '" + declaration.service() + "' declares no `optimizations:` "
                            + "entries — add one to its mavai-services.yaml definition");
        }
        org.mavai.punit.decl.internal.model.OptimizationDeclaration entry;
        if (id == null) {
            if (entries.size() > 1) {
                throw new ContractConfigurationException(
                        "service '" + declaration.service() + "' declares "
                                + entries.size() + " optimizations — name one: "
                                + entries.stream()
                                        .map(org.mavai.punit.decl.internal.model.OptimizationDeclaration::id)
                                        .reduce((a, b) -> a + ", " + b).orElse(""));
            }
            entry = entries.get(0);
        } else {
            entry = entries.stream().filter(e -> e.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ContractConfigurationException(
                            "no optimization '" + id + "' on service '" + declaration.service()
                                    + "' — declared: " + entries.stream()
                                            .map(org.mavai.punit.decl.internal.model.OptimizationDeclaration::id)
                                            .reduce((a, b) -> a + ", " + b).orElse("none")));
        }

        org.mavai.punit.api.spec.FactorsStepper<Map<String, Object>> typedStepper =
                resolveStepper(entry, registry);
        org.mavai.punit.api.spec.Scorer scorer = registry.resolveScorer(entry.scorer());

        Map<String, String> covariateFeed = registry.covariateFeed(declaration.service());
        StockViews views = new StockViews(declaration, registry);
        AtomicReference<Object> currentInput = new AtomicReference<>();
        CriteriaCompiler compiler = new CriteriaCompiler(
                declaration, views, currentInput, Map.of(), null, registry);
        compiler.validateEagerly();
        Criteria<String> criteria = compiler.compile();

        Map<String, Object> initial = new java.util.LinkedHashMap<>(
                services.baselineConfiguration(declaration.service()));
        initial.putAll(entry.initial());
        // Validate iteration 0's configuration at load.
        services.configurePoint(declaration.service(), initial);

        int perIteration = optimizeBudget(declaration);
        List<Object> inputs = declaration.inputs().stream()
                .map(InputDeclaration::value)
                .toList();
        Sampling<Map<String, Object>, Object, String> sampling =
                Sampling.<Map<String, Object>, Object, String>builder()
                        .serviceContractFactory(configuration -> exploreContract(
                                declaration, criteria, currentInput,
                                services, covariateFeed, configuration, inputs, false))
                        .inputs(inputs)
                        .samples(perIteration)
                        .build();

        System.out.println("[PUNIT] run plan: contract '" + declaration.contract()
                + "', optimization '" + entry.id() + "', up to " + entry.maxIterations()
                + " iterations x " + perIteration + " samples — optimize");

        var builder = PUnit.optimizing(sampling)
                .initialFactors(initial)
                .stepper(typedStepper)
                .maxIterations(entry.maxIterations())
                .experimentId(entry.id());
        builder = entry.objective()
                        == org.mavai.punit.decl.internal.model.OptimizationDeclaration.Objective.MINIMIZE
                ? builder.minimize(scorer)
                : builder.maximize(scorer);
        builder = entry.noImprovementWindow() != null
                ? builder.noImprovementWindow(entry.noImprovementWindow())
                : builder.disableEarlyTermination();
        builder.run();
    }

    /**
     * Resolves an entry's {@code stepper:} name against the two stepper
     * populations: built-ins via the {@link org.mavai.punit.decl.spi.StepperProvider}
     * ServiceLoader seam, user factories from the bindings class. A
     * built-in validates its own {@code stepper-config:}; a user
     * factory's parameter list is the schema. Built-in names are
     * unshadowable, mirroring the service-type registry.
     */
    private org.mavai.punit.api.spec.FactorsStepper<Map<String, Object>> resolveStepper(
            org.mavai.punit.decl.internal.model.OptimizationDeclaration entry,
            BindingsRegistry registry) {
        Map<String, org.mavai.punit.decl.spi.StepperProvider> builtIns =
                new java.util.LinkedHashMap<>();
        for (org.mavai.punit.decl.spi.StepperProvider provider
                : java.util.ServiceLoader.load(
                        org.mavai.punit.decl.spi.StepperProvider.class,
                        org.mavai.punit.decl.spi.StepperProvider.class.getClassLoader())) {
            builtIns.put(provider.name(), provider);
        }
        for (String name : builtIns.keySet()) {
            if (registry.hasStepper(name)) {
                throw new ContractConfigurationException(
                        "@Stepper(\"" + name + "\") in "
                                + registry.bindingsClass().getSimpleName()
                                + " shadows the built-in stepper of that name — built-in "
                                + "stepper names cannot be re-registered");
            }
        }
        org.mavai.punit.decl.spi.StepperProvider builtIn = builtIns.get(entry.stepper());
        if (builtIn != null) {
            return builtIn.create(entry.stepperConfig());
        }
        java.lang.reflect.Method stepperFactory =
                registry.stepperFactory(entry.stepper(), builtIns.keySet());
        Object[] stepperArguments = ConfigBinding.bind(
                "optimization '" + entry.id() + "'", entry.stepper(), stepperFactory,
                entry.stepperConfig());
        Object stepper = registry.invoke(stepperFactory, stepperArguments);
        if (!(stepper instanceof org.mavai.punit.api.spec.FactorsStepper)) {
            throw new ContractConfigurationException(
                    "@Stepper(\"" + entry.stepper() + "\") must return an "
                            + "org.mavai.punit.api.spec.FactorsStepper, got "
                            + (stepper == null ? "null" : stepper.getClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        org.mavai.punit.api.spec.FactorsStepper<Map<String, Object>> typed =
                (org.mavai.punit.api.spec.FactorsStepper<Map<String, Object>>) stepper;
        return typed;
    }

    /** Optimize is sized per iteration: property, builder, then the default 5. */
    private int optimizeBudget(ContractDeclaration declaration) {
        String property = "punit.samplesPerIteration." + declaration.contract();
        String propertyValue = System.getProperty(property);
        if (propertyValue != null) {
            return Integer.parseInt(propertyValue.trim());
        }
        if (samplesPerIteration != null) {
            if (samplesPerIteration < 1) {
                throw new ContractConfigurationException(
                        ".samplesPerIteration(" + samplesPerIteration + ") must be positive");
            }
            return samplesPerIteration;
        }
        return 5;
    }

    private ServiceContract<Map<String, Object>, Object, String> exploreContract(
            ContractDeclaration declaration,
            Criteria<String> criteria,
            AtomicReference<Object> currentInput,
            ServicesResolver services,
            Map<String, String> covariateFeed,
            Map<String, Object> configuration,
            List<Object> inputs,
            boolean lenient) {
        final org.mavai.punit.decl.spi.ConfiguredService point;
        if (lenient) {
            org.mavai.punit.decl.spi.ServiceType.ExplorePoint explorePoint =
                    services.explorePoint(declaration.service(), configuration);
            if (explorePoint.note() != null) {
                System.out.println("[PUNIT] note: service '" + declaration.service() + "': "
                        + explorePoint.note());
            }
            point = explorePoint.service();
        } else {
            point = services.configurePoint(declaration.service(), configuration);
        }
        inputs.forEach(point::admit);
        Map<String, String> covariates =
                new java.util.LinkedHashMap<>(point.configurationCovariates());
        covariates.putAll(covariateFeed);
        return new ServiceContract<>() {
            @Override
            public Criteria<String> criteria() {
                return criteria;
            }

            @Override
            public Outcome<String> invoke(Object input, TokenTracker tracker) {
                currentInput.set(input);
                return point.invoke(input, tracker::recordTokens);
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
        };
    }

    /** Explore is sized per configuration: property, builder, then the descriptive default 5. */
    private int exploreBudget(ContractDeclaration declaration) {
        String property = "punit.samplesPerConfig." + declaration.contract();
        String propertyValue = System.getProperty(property);
        if (propertyValue != null) {
            return Integer.parseInt(propertyValue.trim());
        }
        if (samplesPerConfig != null) {
            if (samplesPerConfig < 1) {
                throw new ContractConfigurationException(
                        ".samplesPerConfig(" + samplesPerConfig + ") must be positive");
            }
            return samplesPerConfig;
        }
        return 5;
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
        ServicesResolver services = ServicesResolver.resolve(
                caller, servicesFile, registry, ServicesResolver.Posture.STRICT);
        final org.mavai.punit.decl.spi.ConfiguredService defined =
                services.lookup(declaration.service());
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
                // A configured service keeps its token channel — the
                // cost accounting listens through the sink; a bare code
                // binding has no token notion.
                if (defined != null) {
                    return defined.invoke(input, tracker::recordTokens);
                }
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
        if (defined != null) {
            inputs.forEach(defined::admit);
        }
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
