# PUnit: The Experimentation and Probabilistic Testing Framework

## Governance and Sponsorship Transparency

<p align="center">
  <a href="https://karakun.com">
    <img src="media/karakun-logo.PNG" alt="Karakun" width="220"/>
  </a>
</p>

PUnit is proudly sponsored by [Karakun](https://karakun.com), a Swiss software engineering consultancy specialising in scalable systems, AI, and enterprise-grade solutions.

## Independence

All technical decisions within PUnit are made independently of its sponsor.

This includes:
- Statistical models and assumptions
- Test semantics and verdict logic
- API design and framework behaviour

## Rationale

PUnit aims to provide a neutral and rigorous approach to testing stochastic systems. Maintaining independence from commercial influence is essential to its credibility, particularly in regulated environments such as finance and healthcare.

---


## Why PUnit?

PUnit brings statistical rigor to testing non-deterministic systems...

*Experimentation and unit testing at certainty's boundary*

[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.13%2B-green.svg)](https://junit.org/junit5/)

Some systems do not yield the same outcome on every run — LLMs, ML models, randomized algorithms, network-dependent services. A conventional unit test pretends certainty: one run, one verdict. PUnit makes the boundary explicit: it turns repeated samples into statistical evidence, and reports how strong that evidence is.

## Features

### Experimentation

| Mode            | Description                                                                  |
|-----------------|------------------------------------------------------------------------------|
| **EXPLORE**     | Compare the impact of different configurations with minimal samples          |
| **OPTIMIZE**    | Auto-tune a factor iteratively to find the optimal value for production      |
| **MEASURE**     | Generate an empirical baseline spec to power probabilistic tests             |

### Testing

| Feature                    | Description                                                          |
|----------------------------|----------------------------------------------------------------------|
| **Normative thresholds**   | Test against SLA/SLO/policy thresholds with provenance tracking      |
| **Spec-driven thresholds** | Derive pass/fail thresholds from empirical data, not guesswork       |
| **Early termination**      | Stop early when failure is inevitable or success is guaranteed       |
| **Budget control**         | Time and token budgets at method, class, or suite level              |
| **Pacing constraints**     | Declare API rate limits; framework computes optimal execution pace   |
| **Latency assertions**     | Per-percentile latency thresholds (p50, p90, p95, p99)               |
| **HTML report**            | Standalone report with per-test statistical detail                   |
| **Sentinel**               | JUnit-free engine for probabilistic testing in deployed environments |

## Project structure

PUnit ships as three published artefacts plus a Gradle plugin:

| Artefact               | Coordinate                 | Purpose                                                                                                          |
|------------------------|----------------------------|------------------------------------------------------------------------------------------------------------------|
| **punit-core**         | `org.mavai:punit-core`     | Foundational library: author-facing API (`ServiceContract`, `Contract`, `Sampling`, criteria), engine, statistics, baselines, runtime entry point. Carries the user-facing `@ProbabilisticTest` and `@Experiment` annotations (meta-annotated with `@Test`). JUnit-free at runtime; sentinel-deployable directly. |
| **punit-sentinel**     | `org.mavai:punit-sentinel` | Sentinel runner for production/scheduled probabilistic checks without a test harness.                            |
| **punit-report**       | `org.mavai:punit-report`   | HTML report generator and verdict-XML reader/writer; auto-registers an XML `VerdictSink` via `ServiceLoader`.    |
| **punit Gradle plugin**| `org.mavai.punit` (plugin) | Auto-configures the `test` task, registers `experiment` / `exp` tasks, supports `-Prun=` filtering.              |

The three library artefacts share the `punit-` prefix; `punit-core` is the foundation, the others extend it.

## Quick Start

### 1. Add Dependency

A probabilistic test calls `PUnit.testing(serviceContract).assertPasses()` inside a regular `@Test` method body — `@ProbabilisticTest` is a marker annotation meta-annotated with `@Test`. Depend on `punit-core` directly, add JUnit Jupiter, and (recommended) add `punit-report` so verdict XML lands on disk:

```kotlin
plugins {
    id("org.mavai.punit") version "0.9.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.mavai:punit-core:0.9.0")
    testImplementation("org.mavai:punit-report:0.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
}
```

**Maven:**

```xml
<dependency>
    <groupId>org.mavai</groupId>
    <artifactId>punit-core</artifactId>
    <version>0.9.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mavai</groupId>
    <artifactId>punit-report</artifactId>
    <version>0.9.0</version>
    <scope>test</scope>
</dependency>
```

Maven users need to configure Surefire/Failsafe — see [MAVEN-CONFIGURATION.md](docs/MAVEN-CONFIGURATION.md).

For sentinel-deployable applications that run probabilistic checks without a test harness, depend on `punit-core` directly (and optionally `punit-sentinel` for the standalone runner). See [Part 9: The Sentinel](docs/USER-GUIDE.md#part-9-the-sentinel) in the user guide.

### 2. Write a Probabilistic Test

A service contract wraps the service call and declares its acceptance criteria:

```java
import static org.mavai.punit.api.ThresholdOrigin.SLA;
import static org.mavai.punit.api.criterion.Criteria.meeting;

public class GreetingService implements ServiceContract<NoFactors, String, String> {
    @Override
    public Outcome<String> invoke(String name, TokenTracker tracker) {
        return Outcome.ok(myService.greet(name));
    }

    @Override
    public Criteria<String> criteria() {
        return meeting().<String>passRate(0.95)
                .contractRef(SLA, "Service Agreement §4.2")
                .satisfies("Greeting is non-empty", s -> s == null || s.isBlank()
                        ? Outcome.fail("empty", "no content")
                        : Outcome.ok());
    }
}
```

`ServiceContract<FT, I, O>` carries three type parameters: `FT` is the factor record (the configuration the service contract is sensitive to — model name, temperature, retry count, …); `I` is the per-sample input; `O` is the output the service returns. When a service contract has no varying factors, declare `FT` as `NoFactors` (an empty record provided by punit) and the framework's no-factor builder overloads do the right thing.

The probabilistic test exercises that service contract with a `Sampling` (factory + inputs + sample count); the contract's criteria are auto-injected:

```java
class GreetingServiceTest {
    @ProbabilisticTest
    void serviceGreetsConsistently() {
        PUnit.testing(Sampling.of(nf -> new GreetingService(), 100,
                        List.of("Alice", "Bob", "Charlie")))
            .assertPasses();
    }
}
```

This runs 100 samples against the contract's declared criteria — a 95% pass-rate target with SLA provenance, recorded against "Service Agreement §4.2". The framework terminates early if success is guaranteed or failure becomes inevitable.

### 3. Run It

```bash
./gradlew test
```

## Examples

Find many examples in the [punitexamples repository](https://github.com/javai-org/punitexamples).

## Documentation

The **[User Guide](docs/USER-GUIDE.md)** is the comprehensive reference for PUnit. It covers the full experimentation-to-testing workflow, the service contract pattern, latency assertions, budget and pacing control, the statistical core, the Sentinel runtime, and the HTML report.

The **[Statistical Companion](docs/STATISTICAL-COMPANION.md)** covers the mathematical foundations for readers who want to understand the inference machinery.

## Requirements

- Java 21+
- JUnit Jupiter 5.13+

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Contributing

Contributions are welcome. All contributions are accepted under Apache 2.0 and
require a [Developer Certificate of Origin](dco.txt) sign-off (`git commit -s`).
See [CONTRIBUTING.md](CONTRIBUTING.md) for details. Please open an issue or pull
request on [GitHub](https://github.com/mavai-org/punit).
