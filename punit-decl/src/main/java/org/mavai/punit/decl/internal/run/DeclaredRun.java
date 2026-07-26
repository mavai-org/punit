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
        ContractLocator.Located located = ContractLocator.locate(caller, methodName, explicitName);
        ContractDeclaration declaration = located.declaration();
        if (declaration.latency() != null) {
            throw new ContractConfigurationException(
                    "the `latency:` block arrives with a later phase of the declarative surface");
        }
        RiskClaims claims = RiskClaims.resolve(declaration, samples, power, tolerateBare, tolerateNamed);
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
        Sizing.Sized sized = Sizing.resolve(declaration, samples);

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
        };

        List<Object> inputs = declaration.inputs().stream()
                .map(InputDeclaration::value)
                .toList();
        Sampling<NoFactors, Object, String> sampling = Sampling.<NoFactors, Object, String>builder()
                .serviceContractFactory(factors -> contract)
                .inputs(inputs)
                .samples(sized.samples())
                .build();

        System.out.println("[PUNIT] run plan: contract '" + declaration.contract() + "', "
                + sized.samples() + " samples — " + sized.provenance());

        PUnit.testing(sampling)
                .intent(declaration.intent() == DeclaredIntent.SMOKE
                        ? TestIntent.SMOKE
                        : TestIntent.VERIFICATION)
                .assertPasses();
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
