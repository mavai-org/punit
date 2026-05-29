# Sentinel Deployment Guide

This guide covers project structure and operational deployment for PUnit Sentinel — the execution engine for running probabilistic tests and experiments in deployed environments. For authoring reliability specifications and the Sentinel programming model, see [Part 10 of the User Guide](USER-GUIDE.md#part-10-the-sentinel).

---

## Why the Sentinel Exists

The stochastic behaviour of certain service contracts — LLM integrations, ML model inference, calls to external services with variable latency — can depend heavily on environmental factors: model versions deployed in a specific region, API provider performance characteristics, infrastructure configuration, network conditions.

PUnit already models these environmental influences through its **covariate** system. Covariates are declared environmental factors — temporal (time of day, weekday vs weekend), infrastructural (region, instance type, API version), or configurational (model, temperature, prompt variant) — that PUnit tracks so it can select the most appropriate baseline for a given execution context. When covariates don't match, PUnit qualifies the verdict with a non-conformance warning, because testing against the wrong baseline produces misleading results.

However, covariate-aware baseline selection works best when baselines are available for the relevant covariate values. For service contracts whose behaviour is shaped by the deployed environment, probabilistic testing must be based on baselines obtained from within that environment. A baseline measured on a developer workstation or in a CI container may not reflect what the system experiences in staging or production.

Because environmental factors change over time — model updates, infrastructure migrations, provider degradation — a one-off baseline is insufficient. We require a mechanism that continuously guards against regression by re-measuring baselines and re-verifying reliability within the target environment.

This is the PUnit Sentinel: an execution engine that runs the same probabilistic tests and experiments defined for development-time testing, but in the deployed environment, producing environment-specific baselines and dispatching verdicts to observability infrastructure.

---

## Reference Module Layout

For a real production deployment a multi-module split keeps responsibilities clean:

```
app-stochastic          punit-core
  ↑         ↑              ↑
app-main    app-usecases ──┘
                  │
                  └──→ createSentinel task ──→ sentinel.jar
```

For a self-contained example a single module is fine — the same `src/main/java` shape works in either case (see `punitexamples` for a single-module reference). What matters is **where the sentinel-deployable classes live**, not how many Gradle modules the project has.

### Module Responsibilities

#### `app-stochastic` — Stochastic Service Integrations

Contains the application's non-deterministic service integrations: LLM client wrappers, ML model interfaces, external API clients with variable latency. These are **plain Java objects** constructable via `new` — no DI framework, no PUnit dependency.

```kotlin
// app-stochastic/build.gradle.kts
dependencies {
    // Only the service's own dependencies (HTTP client, SDK, etc.)
    implementation("com.openai:openai-java:1.0.0")
}
```

```java
// Plain Java — no PUnit, no Spring, no Guice
public class OpenAiClient {
    private final String apiKey;

    public OpenAiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public String chat(String prompt) {
        // Call OpenAI API
    }
}
```

#### `app-usecases` — Service Contracts and Sentinel-Deployable Classes

Contains `ServiceContract` implementations and the sentinel-deployable classes that exercise them. A sentinel-deployable class is just a class declaring one or more `@ProbabilisticTest` and/or `@Experiment` methods — there is **no class-level marker annotation**. The same class is consumed by JUnit at development time (because `@ProbabilisticTest` and `@Experiment` are meta-annotated `@Test`) and by the Sentinel runner at deployment time.

```kotlin
// app-usecases/build.gradle.kts
dependencies {
    api(project(":app-stochastic"))
    api("org.mavai:punit-core:0.6.0")  // Production dependency
}
```

This module depends on `punit-core` via `api()` — not `testImplementation`. This is the defining characteristic of the Sentinel authoring model: PUnit types (`ServiceContract`, `Contract`, `Criteria`, `Sampling`, `PUnit`, etc.) are production dependencies in this module because the sentinel-deployable classes are production artefacts.

The module must **not** depend on `junit-jupiter-api`. Sentinel-deployable code is JUnit-free. For the contract-first authoring model, see [Part 3 of the User Guide](USER-GUIDE.md#part-3-the-use-case).

#### `app-main` — Main Application

The main application module. Uses whatever DI framework is appropriate (Spring Boot, Guice, Micronaut, or none). Depends on `app-stochastic` to access stochastic services. **Has no PUnit dependency whatsoever.**

```kotlin
// app-main/build.gradle.kts
dependencies {
    implementation(project(":app-stochastic"))
    implementation("org.springframework.boot:spring-boot-starter-web:3.4.0")
    // No PUnit dependency
}
```

The DI layer simply instantiates the stochastic service classes from `app-stochastic` as beans — no code duplication, no PUnit awareness.

#### Test Suite — JUnit Probabilistic Tests

The JUnit test source set. Contains standalone `@ProbabilisticTest` and `@Experiment` classes plus test utilities. Sentinel-deployable classes from `app-usecases` are picked up automatically by JUnit alongside these.

```kotlin
// In app-usecases/build.gradle.kts or a dedicated app-tests module
dependencies {
    testImplementation(project(":app-usecases"))
    testImplementation("org.mavai:punit-core:0.7.0")
    testImplementation("org.mavai:punit-report:0.7.0")  // Verdict XML sink
    testImplementation("org.junit.jupiter:junit-jupiter")
}
```

#### Sentinel JAR — Built by the PUnit Gradle Plugin

The PUnit Gradle plugin provides a `createSentinel` task that builds an executable fat JAR from compiled main classes. The task scans for classes declaring `@ProbabilisticTest` or `@Experiment` methods, packages them with their dependencies and the Sentinel runtime, and produces a self-contained executable:

```bash
./gradlew createSentinel
```

The resulting JAR (`build/libs/<project>-sentinel.jar`) includes:

- All sentinel-runnable classes (from main source sets and transitive project dependencies)
- The `punit-sentinel` runtime (`SentinelMain`, `SentinelOrchestrator`, `SentinelExecutor`, verdict sinks)
- All runtime dependencies, unpacked into the fat JAR
- A generated `META-INF/punit/sentinel-classes` manifest

The task requires at least one class declaring `@ProbabilisticTest` or `@Experiment` on the main classpath. The plugin automatically adds `punit-sentinel` as a dependency — no manual dependency declaration is needed beyond the standard `org.mavai.punit` plugin application.

---

## Why Sentinel-Deployable Classes Are Not Test Code

Sentinel-deployable classes define **what to measure** and **what to verify** about stochastic behaviour. They are consumed by both the JUnit test suite (because their `@ProbabilisticTest` / `@Experiment` methods are meta-annotated `@Test`) and the Sentinel runner (which discovers them via the build-time manifest).

Placing them in a test source set makes them unavailable to the Sentinel — code in `src/test/java` is never packaged into a deployable JAR. The `app-usecases` module is the bridge: it depends on `app-stochastic` (to invoke stochastic services) and `punit-core` (for `ServiceContract`, `Contract`, `Criteria`, `PUnit`, etc.), and produces a production artefact consumable by both engines.

This also applies to input data — anything passed to `Sampling.Builder.inputs(...)`. The Sentinel engine needs the same inputs as the JUnit engine. If input data lives in the test source set or a test resource folder, the Sentinel cannot reach it.

---

## Why Stochastic Services Must Be Plain Java

The Sentinel runtime has no DI container. Stochastic services must be constructable without one.

This is not a limitation — it's a **forcing function for clean API boundaries** around non-deterministic behaviour. Applications using Spring, Guice, or other DI frameworks isolate their stochastic integrations in `app-stochastic` as plain Java objects. The main application's DI layer wraps them as beans. No code duplication, no PUnit dependency in the main application.

No framework-specific Sentinel variants are needed or provided. There is no `SpringSentinel` or `GuiceSentinel`. A sentinel-deployable class constructs its stochastic dependencies as plain Java objects (via factory closures on `Sampling.Builder.serviceContractFactory(...)`), and the Sentinel runtime knows nothing about DI frameworks.

---

## The Sentinel Deployment Workflow

### 0. Build the Sentinel JAR

Build the executable sentinel JAR from the project that contains the sentinel-deployable classes:

```bash
./gradlew createSentinel
```

Deploy the resulting `build/libs/<project>-sentinel.jar` to the target environment.

### 1. Discover Available Service Contracts

List the tests and experiments packaged in the JAR:

```bash
java -jar sentinel.jar --list
```

The output groups discovered methods by service contract, distinguishing `@Experiment` methods (which produce baselines, exploration grids, or optimisation histories) from `@ProbabilisticTest` methods (which produce verdicts).

### 2. Establish Baselines (Experiment Mode)

Run measure experiments in the target environment to produce environment-local baselines:

```bash
# Run all experiments
java -Dpunit.spec.dir=/opt/sentinel/baselines -jar sentinel.jar exp

# Run experiments for a specific class
java -Dpunit.spec.dir=/opt/sentinel/baselines -jar sentinel.jar exp --class ShoppingBasketSentinel
```

This scans `@Experiment` methods on each registered class, executes the bodies (which call `PUnit.measuring(...).run()` to produce baselines, or `PUnit.exploring(...) / .optimizing(...)` for exploration / optimisation runs), and writes outputs to the configured directories. The baseline output directory is required for measure experiments and can be set via `-Dpunit.spec.dir` or the `PUNIT_SPEC_DIR` environment variable.

### 3. Verify Against Baselines (Test Mode)

Run probabilistic tests against the current baselines:

```bash
# Run all tests
java -jar sentinel.jar test

# Run tests for a specific class
java -jar sentinel.jar test --class PaymentGatewaySentinel

# Run with per-sample progress output
java -jar sentinel.jar test --verbose
```

This scans `@ProbabilisticTest` methods, executes the bodies (which call `PUnit.testing(...).criterion(...).assertPasses()`), resolves baselines via the layered `BaselineProvider` (environment-local first, classpath fallback), derives thresholds, and dispatches verdicts.

### 4. Verdict Dispatch

Verdicts are dispatched to all configured `VerdictSink` instances. Each verdict carries:

- A correlation ID (e.g., `v:a3f8c2`) for cross-referencing with JUnit reports and observability systems
- Environment metadata (`PUNIT_ENVIRONMENT`, `PUNIT_INSTANCE_ID`)
- Full statistical detail (observed pass rate, threshold, confidence interval, per-dimension breakdown)

---

## Scheduling Is the Deployer's Responsibility

The Sentinel is a library, not a daemon. It does not schedule its own execution, manage cron expressions, or run a background loop. `SentinelOrchestrator.run(...)` executes when called, returns a `SentinelResult`, and that's it. How and when it is called is entirely up to the deployer.

This is a deliberate design choice. Scheduling infrastructure varies widely across deployment environments, and PUnit has no reason to reinvent or constrain it. The deployer selects the mechanism that fits their operational context:

- **Cron or systemd timer** for simple periodic execution on a VM
- **Kubernetes CronJob** for containerised deployments
- **Cloud schedulers** such as AWS EventBridge, GCP Cloud Scheduler, or Azure Timer Triggers
- **CI/CD pipelines** as a post-deployment verification step
- **Application-embedded scheduling** via Quartz, Spring `@Scheduled`, or similar, if the Sentinel runs within an existing application process

The Sentinel's exit code (`SentinelResult.allPassed()`) and verdict dispatch (`VerdictSink`) provide the integration points. The deployer wires these into their alerting, dashboarding, or circuit-breaking infrastructure as appropriate.

---

## Dependency Summary

| Module           | PUnit Dependency          | JUnit Dependency       | Purpose                                                |
|------------------|---------------------------|------------------------|--------------------------------------------------------|
| `app-stochastic` | None                      | None                   | Stochastic service integrations                        |
| `app-main`       | None                      | None                   | Main application                                       |
| `app-usecases`   | `punit-core` (production) | None                   | Service contracts + sentinel-deployable classes                |
| Test suite       | `punit-core` (test) + `punit-report` (test) | `junit-jupiter` (test) | JUnit-driven probabilistic tests, experiments, fixtures|

The sentinel JAR is built by the PUnit Gradle plugin's `createSentinel` task from the main classpath — no dedicated sentinel module is needed. The plugin automatically includes `punit-sentinel` and its transitive dependencies.

---

## PUnit Artefact Selection

| Consumer                            | Artefact                   | Scope                |
|-------------------------------------|----------------------------|----------------------|
| Sentinel-deployable / use-case author | `org.mavai:punit-core`     | `api` (production)   |
| JUnit test developer                | `org.mavai:punit-core` + `org.mavai:punit-report` + `org.junit.jupiter:junit-jupiter` | `testImplementation` |
| Verdict-XML / report consumer       | `org.mavai:punit-report`   | as appropriate       |

The `punit-sentinel` artefact is included automatically by the PUnit Gradle plugin when building the sentinel JAR via `createSentinel`. No manual dependency declaration is needed for sentinel deployment.
