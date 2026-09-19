# Maven Configuration for PUnit

PUnit's Gradle plugin automates experiment task setup. For Maven users, the equivalent configuration uses Surefire and Failsafe with JUnit 5 tag filtering.

## JUnit Discovery

`@ProbabilisticTest` and `@Experiment` are meta-annotated with JUnit Jupiter's `@Test`, so Jupiter discovers them as ordinary test methods. No JUnit extensions are registered and no ServiceLoader auto-detection is required — the framework runs entirely inside the test method body via `PUnit.testing(...)`, `PUnit.measuring(...)`, `PUnit.exploring(...)`, or `PUnit.optimizing(...)`.

## Test Task Configuration (Surefire)

Exclude experiment-tagged tests from regular test runs:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <excludedGroups>punit-experiment</excludedGroups>
        <systemPropertyVariables>
            <!-- Forward punit.* properties as needed -->
            <punit.stats.detailLevel>${punit.stats.detailLevel}</punit.stats.detailLevel>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

## Experiment Profile

Run experiments via a dedicated Maven profile:

```xml
<profile>
    <id>experiments</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.4</version>
                <configuration>
                    <!-- Include only experiment-tagged tests -->
                    <groups>punit-experiment</groups>

                    <!-- Output directories for each experiment mode -->
                    <systemPropertyVariables>
                        <!-- MEASURE baselines are inputs to @ProbabilisticTest
                             and are committed to the repo. EXPLORE and OPTIMIZE
                             outputs are human-review artefacts and default to
                             target/punit/ (Maven build output). -->
                        <punit.specs.outputDir>src/test/resources/punit/baselines</punit.specs.outputDir>
                        <punit.explorations.outputDir>${project.build.directory}/punit/explorations</punit.explorations.outputDir>
                        <punit.optimizations.outputDir>${project.build.directory}/punit/optimizations</punit.optimizations.outputDir>
                    </systemPropertyVariables>

                    <!-- Deactivate @Disabled so experiments can run -->
                    <configurationParameters>
                        junit.jupiter.conditions.deactivate = org.junit.*DisabledCondition
                    </configurationParameters>

                    <!-- Experiments are exploratory — don't fail the build -->
                    <testFailureIgnore>true</testFailureIgnore>

                    <!-- Verbose output -->
                    <reportFormat>plain</reportFormat>
                    <trimStackTrace>false</trimStackTrace>
                    <useFile>false</useFile>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Usage:

```bash
# Run all experiments
mvn test -Pexperiments

# Run a specific experiment class
mvn test -Pexperiments -Dtest=ShoppingBasketMeasure

# Run a specific experiment method
mvn test -Pexperiments -Dtest=ShoppingBasketMeasure#measureRealisticSearchBaseline
```

## Failsafe Alternative

If you prefer to use Failsafe for experiments (keeping Surefire for unit tests):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <groups>punit-experiment</groups>
        <systemPropertyVariables>
            <punit.specs.outputDir>src/test/resources/punit/baselines</punit.specs.outputDir>
            <punit.explorations.outputDir>${project.build.directory}/punit/explorations</punit.explorations.outputDir>
            <punit.optimizations.outputDir>${project.build.directory}/punit/optimizations</punit.optimizations.outputDir>
        </systemPropertyVariables>
        <configurationParameters>
            junit.jupiter.conditions.deactivate = org.junit.*DisabledCondition
        </configurationParameters>
        <testFailureIgnore>true</testFailureIgnore>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Usage:

```bash
mvn verify -Dgroups=punit-experiment
```

## Test Subject Exclusion

If your project uses PUnit's TestKit pattern (test subjects in `**/testsubjects/**`), exclude them from direct discovery:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/testsubjects/**</exclude>
        </excludes>
    </configuration>
</plugin>
```

## Reports: the `mavai` renderer

PUnit renders no HTML; the family's shared `mavai` executable does, from the artefacts a run writes (see the User Guide, Part 11). The Gradle plugin resolves and runs it for you; a Maven build does the same in two steps, because the renderer is on Maven Central as `org.mavai:mavai` with one platform-classified `exe` artefact per platform.

Detect the platform with `os-maven-plugin`, which sets `${os.detected.classifier}` to the same vocabulary the artefacts are published under (`linux-x86_64`, `linux-aarch_64`, `osx-x86_64`, `osx-aarch_64`, `windows-x86_64`):

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
</build>
```

Copy the executable into the build and run it after the tests:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>mavai-renderer</id>
            <phase>post-integration-test</phase>
            <goals><goal>copy</goal></goals>
            <configuration>
                <artifactItems>
                    <artifactItem>
                        <groupId>org.mavai</groupId>
                        <artifactId>mavai</artifactId>
                        <version>0.21.0</version>
                        <classifier>${os.detected.classifier}</classifier>
                        <type>exe</type>
                        <destFileName>mavai</destFileName>
                    </artifactItem>
                </artifactItems>
                <outputDirectory>${project.build.directory}/mavai</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>mavai-verdict</id>
            <phase>post-integration-test</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
                <executable>${project.build.directory}/mavai/mavai</executable>
                <arguments>
                    <argument>verdict</argument>
                    <argument>${project.build.directory}/reports/punit</argument>
                    <argument>-o</argument>
                    <argument>${project.build.directory}/reports/punit/verdict.html</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

On Windows the copied file needs the `.exe` name (`<destFileName>mavai.exe</destFileName>`) to be a command; on the other platforms mark it executable before running it (the `copy` goal does not). `mavai explore <explorations>/<service>` and `mavai optimize <optimizations>` render the experiment artefacts the same way.
