package org.mavai.punit.decl.internal.run;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.criterion.Criteria;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.model.ContractDeclaration;
import org.mavai.punit.decl.model.DeclaredIntent;
import org.mavai.punit.decl.model.InputDeclaration;
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
        BindingsRegistry.Invoker invoker = registry.resolve(declaration.service());
        StockViews views = new StockViews(declaration);
        AtomicReference<Object> currentInput = new AtomicReference<>();
        CriteriaCompiler compiler = new CriteriaCompiler(
                declaration, views, currentInput, claims.tolerateByCriterion(), claims.power());
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
