package org.mavai.punit.decl.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.CriterionDeclaration;
import org.mavai.punit.decl.internal.model.DeclaredIntent;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.internal.model.InputDeclaration;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.decl.spi.MessageParts;
import org.mavai.punit.decl.internal.model.PostconditionForm;

@DisplayName("Contract-file parser")
class ContractParserTest {

    private static final String MINIMAL = """
            format: mavai-contract/1
            contract: greeting-service-is-polite
            service: greeting-service
            criteria:
              - threshold: 0.95
                contains: "hello"
            inputs:
              - "Alice"
              - "Bob"
            """;

    private static void assertRefused(String yaml, String messageFragment) {
        ThrowingCallable parse = () -> ContractParser.parse(yaml);
        assertThatThrownBy(parse)
                .isInstanceOf(ContractConfigurationException.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(messageFragment);
    }

    @Nested
    @DisplayName("acceptance")
    class Acceptance {

        @Test
        @DisplayName("the specification's minimal contract parses")
        void minimalContract() {
            ContractDeclaration declaration = ContractParser.parse(MINIMAL);
            assertThat(declaration.contract()).isEqualTo("greeting-service-is-polite");
            assertThat(declaration.service()).isEqualTo("greeting-service");
            assertThat(declaration.intent()).isEqualTo(DeclaredIntent.VERIFICATION);
            assertThat(declaration.confidence()).isEqualTo(0.95);
            assertThat(declaration.confidenceDeclared()).isFalse();
            assertThat(declaration.inputs()).extracting(InputDeclaration::value).containsExactly("Alice", "Bob");
            assertThat(declaration.criteria()).hasSize(1);
            CriterionDeclaration criterion = declaration.criteria().get(0);
            assertThat(criterion.threshold()).isEqualTo(0.95);
            assertThat(criterion.forms()).hasSize(1);
            assertThat(criterion.forms().get(0).form()).isEqualTo(PostconditionForm.CONTAINS);
            assertThat(criterion.name()).isEqualTo("criterion-1-contains");
        }

        @Test
        @DisplayName("multiple criteria are several entries, empirical beside normative")
        void multiCriterion() {
            ContractDeclaration declaration = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: fortune-teller-is-usually-encouraging
                    service: fortune-teller
                    criteria:
                      - name: fortune-is-delivered
                        threshold: 0.8
                        matches: "fortune|favour"
                      - name: spirits-stay-polite
                        contains: "."
                    inputs: ["Alice"]
                    """);
            assertThat(declaration.criteria()).hasSize(2);
            assertThat(declaration.criteria().get(0).threshold()).isEqualTo(0.8);
            assertThat(declaration.criteria().get(1).threshold()).isNull();
        }

        @Test
        @DisplayName("views, subjects, and path qualifiers parse in declaration order")
        void viewsAndPaths() {
            ContractDeclaration declaration = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: basket-builder-returns-valid-baskets
                    service: basket-builder
                    transforms:
                      basket: json
                      judged: basket-judge
                    criteria:
                      - threshold: 0.95
                        postconditions:
                          - matches: '^\\s*\\{'
                          - in: basket
                            path: "$.items[*].name"
                            matches: '\\w'
                          - parses: judged
                          - satisfies: looks-right
                    inputs: ["a dozen eggs"]
                    """);
            assertThat(declaration.transforms())
                    .containsEntry("basket", "json")
                    .containsEntry("judged", "basket-judge");
            var forms = declaration.criteria().get(0).forms();
            assertThat(forms).hasSize(4);
            assertThat(forms.get(0).view()).isEqualTo("raw");
            assertThat(forms.get(1).view()).isEqualTo("basket");
            assertThat(forms.get(1).path()).isEqualTo("$.items[*].name");
            assertThat(forms.get(2).form()).isEqualTo(PostconditionForm.PARSES);
            assertThat(forms.get(3).form()).isEqualTo(PostconditionForm.SATISFIES);
        }

        @Test
        @DisplayName("per-input expectations carry their structural position")
        void perInputExpectations() {
            ContractDeclaration declaration = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: basket-builder-honours-each-instruction
                    service: basket-builder
                    transforms:
                      basket: json
                    criteria:
                      - threshold: 0.8
                        postconditions:
                          - in: basket
                            path: "$.items[*].name"
                            matches: '\\w'
                    inputs:
                      - "just a plain input"
                      - input: "a dozen eggs, please"
                        expected:
                          - in: basket
                            path: "$.items[*].name"
                            equals: "egg"
                      - input: "two bottles of milk"
                        expected: { contains: "milk" }
                    """);
            assertThat(declaration.inputs()).hasSize(3);
            assertThat(declaration.inputs().get(0).hasExpectations()).isFalse();
            assertThat(declaration.inputs().get(1).hasExpectations()).isTrue();
            assertThat(declaration.inputs().get(2).expected()).hasSize(1);
            assertThat(declaration.hasInputExpectations()).isTrue();
        }

        @Test
        @DisplayName("provenance and risk claims parse onto the criterion")
        void provenanceAndClaims() {
            ContractDeclaration declaration = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: payment-meets-its-published-bar
                    service: payment-gateway
                    confidence: 0.9
                    criteria:
                      - name: reference-is-well-formed
                        threshold: 0.99
                        threshold-origin: sla
                        contract-ref: "Payment Provider SLA v2.0 §4.1"
                        matches: "RF-\\\\d{8}"
                      - name: no-worse-than-measured
                        tolerate: 0.9
                        confidence: 0.99
                        contains: "confirmed"
                    inputs: ["refund order 1832"]
                    """);
            assertThat(declaration.confidenceDeclared()).isTrue();
            assertThat(declaration.confidence()).isEqualTo(0.9);
            CriterionDeclaration normative = declaration.criteria().get(0);
            assertThat(normative.thresholdOrigin()).isEqualTo("sla");
            assertThat(normative.contractRef()).contains("SLA v2.0");
            CriterionDeclaration empirical = declaration.criteria().get(1);
            assertThat(empirical.tolerate()).isEqualTo(0.9);
            assertThat(empirical.confidence()).isEqualTo(0.99);
        }

        @Test
        @DisplayName("both latency shapes parse")
        void latencyShapes() {
            ContractDeclaration explicit = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: payment-meets-latency-sla
                    service: payment-gateway
                    latency:
                      p50: 200
                      p95: 500
                      confidence: 0.9
                      threshold-origin: slo
                      contract-ref: "Payments SLO 2026 §2"
                    criteria: [{ threshold: 0.95, contains: "confirmed" }]
                    inputs: ["refund order 1832"]
                    """);
            assertThat(explicit.latency().ceilings()).hasSize(2);
            assertThat(explicit.latency().confidence()).isEqualTo(0.9);

            ContractDeclaration empirical = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: triage-stays-as-fast-as-measured
                    service: triage-assistant
                    latency:
                      empirical: [p99, p95]
                    criteria: [{ threshold: 0.7, contains: "category" }]
                    inputs: ["my account is locked"]
                    """);
            assertThat(empirical.latency().empirical()).containsExactly("p95", "p99");
        }

        @Test
        @DisplayName("input shapes: scalars, argument tuples, parts, and part lists")
        void inputShapes(@TempDir Path directory) throws IOException {
            Files.writeString(directory.resolve("note.txt"), "the quick brown fox");
            Files.write(directory.resolve("swatch.png"), new byte[] {1, 2, 3});
            Files.write(directory.resolve("clip.m4a"), new byte[] {4, 5});
            Path contract = directory.resolve("contract.yaml");
            Files.writeString(contract, """
                    format: mavai-contract/1
                    contract: assistant-handles-every-input-shape
                    service: assistant
                    intent: smoke
                    criteria: [{ threshold: 0.8, matches: '\\w' }]
                    inputs:
                      - "a plain scalar instruction"
                      - ["milk", 2, true]
                      - - text: "What colour dominates this image?"
                        - image: ./swatch.png
                      - - text: { file: ./note.txt }
                      - input:
                          - audio: ./clip.m4a
                        expected:
                          - contains: "fox"
                    """);
            ContractDeclaration declaration = ContractParser.load(contract);
            assertThat(declaration.intent()).isEqualTo(DeclaredIntent.SMOKE);
            assertThat(declaration.inputs()).hasSize(5);
            assertThat(declaration.inputs().get(1).value()).isEqualTo(List.of("milk", 2, true));
            assertThat(declaration.inputs().get(2).value()).isInstanceOf(MessageParts.class);
            MessageParts message = (MessageParts) declaration.inputs().get(2).value();
            assertThat(message.parts().get(0)).isEqualTo("What colour dominates this image?");
            assertThat(((FileInput) message.parts().get(1)).kind()).isEqualTo(MediaKind.IMAGE);
            assertThat(declaration.inputs().get(3).value()).isEqualTo("the quick brown fox");
            assertThat(((FileInput) declaration.inputs().get(4).value()).kind()).isEqualTo(MediaKind.AUDIO);
            assertThat(declaration.inputs().get(4).expected()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("top-level refusals")
    class TopLevelRefusals {

        @Test
        @DisplayName("a wrong format identifier is refused")
        void formatIdentifier() {
            assertRefused(MINIMAL.replace("mavai-contract/1", "mavai-task/1"),
                    "`format:` must be 'mavai-contract/1'");
        }

        @Test
        @DisplayName("a missing required key is refused")
        void missingRequiredKey() {
            assertRefused(MINIMAL.replace("service: greeting-service\n", ""),
                    "missing required key `service:`");
        }

        @Test
        @DisplayName("an unknown key is refused naming it")
        void unknownKey() {
            assertRefused("flavour: vanilla\n" + MINIMAL, "unknown key `flavour:`");
        }

        @Test
        @DisplayName("a reserved seam key is refused with the seam pointer")
        void reservedSeamKey() {
            assertRefused("facets: {}\n" + MINIMAL, "reserved by the mavai contract format");
            assertRefused("covariates: {}\n" + MINIMAL, "reserved by the mavai contract format");
            assertRefused("budget: {}\n" + MINIMAL, "reserved by the mavai contract format");
        }

        @Test
        @DisplayName("withdrawn sizing keys are refused naming the builder call and property")
        void withdrawnSizingKeys() {
            assertRefused("samples: 100\n" + MINIMAL, "the contract carries the claim");
            assertRefused("samples: 100\n" + MINIMAL, ".samples(N)");
            assertRefused("samples-per-config: 5\n" + MINIMAL, ".samplesPerConfig(N)");
        }

        @Test
        @DisplayName("the withdrawn run-mode key is refused")
        void withdrawnRunModeKey() {
            assertRefused("kind: test\n" + MINIMAL, "`kind:` was withdrawn");
        }

        @Test
        @DisplayName("an unknown intent is refused")
        void intentVocabulary() {
            assertRefused("intent: production\n" + MINIMAL,
                    "unknown `intent: production` — expected verification or smoke");
        }

        @Test
        @DisplayName("a confidence outside the unit interval is refused")
        void confidenceRange() {
            assertRefused("confidence: 1.2\n" + MINIMAL, "`confidence:` must be a number in (0, 1)");
        }

        @Test
        @DisplayName("malformed YAML is wrapped in the author's vocabulary")
        void malformedYaml() {
            assertRefused("contract: [unclosed", "not well-formed YAML");
        }
    }

    @Nested
    @DisplayName("criteria refusals")
    class CriteriaRefusals {

        @Test
        @DisplayName("an empty criteria list is refused")
        void criteriaEmpty() {
            assertRefused(MINIMAL.replaceFirst("(?s)criteria:.*inputs:", "criteria: []\ninputs:"),
                    "`criteria:` must be a non-empty list");
        }

        @Test
        @DisplayName("a threshold outside the unit interval is refused")
        void thresholdRange() {
            assertRefused(MINIMAL.replace("threshold: 0.95", "threshold: 1.5"),
                    "`threshold:` must be a number in (0, 1)");
        }

        @Test
        @DisplayName("the reserved empirical threshold form is refused with the seam pointer")
        void thresholdEmpiricalReserved() {
            assertRefused(MINIMAL.replace("threshold: 0.95", "threshold: empirical"),
                    "`threshold: empirical` is reserved");
        }

        @Test
        @DisplayName("tolerate beside a declared threshold is refused")
        void tolerateWithThreshold() {
            assertRefused(MINIMAL.replace("threshold: 0.95", "threshold: 0.95\n    tolerate: 0.9"),
                    "no baseline claim to protect");
        }

        @Test
        @DisplayName("a per-criterion confidence beside a declared threshold is refused")
        void confidenceWithThreshold() {
            assertRefused(MINIMAL.replace("threshold: 0.95", "threshold: 0.95\n    confidence: 0.99"),
                    "belongs to an empirical criterion");
        }

        @Test
        @DisplayName("an unknown provenance origin is refused — the set is closed")
        void thresholdOriginVocabulary() {
            assertRefused(
                    MINIMAL.replace("threshold: 0.95", "threshold: 0.95\n    threshold-origin: contract"),
                    "provenance category");
        }

        @Test
        @DisplayName("a criterion without any form of its own is refused")
        void criterionWithoutForm() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    criteria:
                      - name: bare
                        threshold: 0.9
                    inputs: ["Alice"]
                    """, "declares no postcondition form");
        }

        @Test
        @DisplayName("duplicate criterion names are refused")
        void duplicateNames() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    criteria:
                      - { name: twin, threshold: 0.9, contains: "a" }
                      - { name: twin, threshold: 0.8, contains: "b" }
                    inputs: ["Alice"]
                    """, "criterion names must be unique");
        }

        @Test
        @DisplayName("check-level qualifiers do not belong on the criterion")
        void misplacedQualifiers() {
            assertRefused(MINIMAL.replace("contains: \"hello\"", "contains: \"hello\"\n    in: basket"),
                    "does not belong on the criterion");
            assertRefused(MINIMAL.replace("contains: \"hello\"", "contains: \"hello\"\n    path: \"$.x\""),
                    "does not belong on the criterion");
        }

        @Test
        @DisplayName("an unknown criterion key is refused")
        void unknownCriterionKey() {
            assertRefused(MINIMAL.replace("contains: \"hello\"", "contains: \"hello\"\n    weight: 2"),
                    "unknown key `weight:`");
        }
    }

    @Nested
    @DisplayName("postcondition refusals")
    class PostconditionRefusals {

        @Test
        @DisplayName("each postcondition declares exactly one form")
        void formCardinality() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - equals: "a"
                            contains: "b"
                    inputs: ["Alice"]
                    """, "exactly one form");
        }

        @Test
        @DisplayName("path without a subject view is refused")
        void pathWithoutIn() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - path: "$.items[*].name"
                            matches: '\\w'
                    inputs: ["Alice"]
                    """, "`path:` requires `in:`");
        }

        @Test
        @DisplayName("path on a form outside the path-capable vocabulary is refused")
        void pathOnNonStringForm() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    transforms:
                      basket: json
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - in: basket
                            path: "$.items"
                            satisfies: looks-right
                    inputs: ["Alice"]
                    """, "string and value-comparison forms only");
        }

        @Test
        @DisplayName("a subject naming an undeclared view is refused naming the declared ones")
        void undeclaredView() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - in: basket
                            contains: "milk"
                    inputs: ["Alice"]
                    """, "names an undeclared view");
        }

        @Test
        @DisplayName("parses takes no subject qualifier")
        void parsesWithIn() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    transforms:
                      basket: json
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - in: basket
                            parses: basket
                    inputs: ["Alice"]
                    """, "takes no `in:`");
        }

        @Test
        @DisplayName("parses references a declared view")
        void parsesUndeclaredView() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - parses: basket
                    inputs: ["Alice"]
                    """, "`parses:` references a declared view");
        }

        @Test
        @DisplayName("form arguments are type-checked at parse")
        void formArgumentTypes() {
            assertRefused(MINIMAL.replace("contains: \"hello\"", "equals: 6"), "`equals:` takes a string");
            assertRefused(MINIMAL.replace("contains: \"hello\"", "one-of: []"),
                    "`one-of:` takes a non-empty list of strings");
        }

        @Test
        @DisplayName("an unknown form is refused")
        void unknownForm() {
            assertRefused(MINIMAL.replace("contains: \"hello\"", "postconditions: [{ resembles: \"x\" }]"),
                    "unknown postcondition form `resembles`");
        }
    }

    @Nested
    @DisplayName("value-comparison forms")
    class ValueComparisonForms {

        @Test
        @DisplayName("the scalar value-comparison vocabulary parses, per input too")
        void scalarForms() {
            ContractDeclaration declaration = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: quote-service-extracts-exact-values
                    service: quote-service
                    transforms:
                      quote: json
                    criteria:
                      - name: extracted-values-match-the-document
                        threshold: 0.95
                        not-equals: "ERROR"
                        postconditions:
                          - in: quote
                            path: "$.premium"
                            eq: 2637.80
                          - in: quote
                            path: "$.excess"
                            eq: "500.00"
                          - in: quote
                            path: "$.instalment-fee"
                            ne: 0
                          - in: quote
                            path: "$.items[*].price"
                            ge: 0
                          - in: quote
                            path: "$.tax-rate"
                            lt: "0.2"
                          - in: quote
                            path: "$.term-months"
                            gt: 0
                          - in: quote
                            path: "$.instalments"
                            le: 12
                          - in: quote
                            path: "$.holder"
                            equals-ci: "Frau  Beispiel"
                          - in: quote
                            path: "$.status"
                            not-equals: "declined"
                          - in: quote
                            path: "$.cancellation-date"
                            is-null: true
                    inputs:
                      - "quote the sample policy"
                      - input: "quote the premium-only policy"
                        expected:
                          - in: quote
                            path: "$.premium"
                            eq: "1049.10"
                    """);
            var forms = declaration.criteria().get(0).forms();
            assertThat(forms).extracting(f -> f.form()).containsExactly(
                    PostconditionForm.NOT_EQUALS,
                    PostconditionForm.EQ, PostconditionForm.EQ, PostconditionForm.NE,
                    PostconditionForm.GE, PostconditionForm.LT, PostconditionForm.GT,
                    PostconditionForm.LE, PostconditionForm.EQUALS_CI,
                    PostconditionForm.NOT_EQUALS, PostconditionForm.IS_NULL);
            assertThat(forms.get(0).view()).isEqualTo("raw");
            assertThat(declaration.inputs().get(1).expected().get(0).form())
                    .isEqualTo(PostconditionForm.EQ);
        }

        @Test
        @DisplayName("the set forms and the boolean form parse, per input too")
        void setAndBooleanForms() {
            ContractDeclaration declaration = ContractParser.parse("""
                    format: mavai-contract/1
                    contract: annotator-returns-the-gold-set
                    service: buildings-annotator
                    transforms:
                      doc: json
                    criteria:
                      - name: extracted-sets-match-the-document
                        threshold: 0.9
                        postconditions:
                          - in: doc
                            path: "$.buildings[*].name"
                            equals-set: ["Hauptgebäude", "Nebengebäude", "Nebengebäude"]
                          - in: doc
                            path: "$.rents[*].amount"
                            contains-set: [1200, 950.50]
                          - in: doc
                            path: "$.rents[*].amount"
                            count-equals: 2
                          - in: doc
                            path: "$.buildings[0].isIncluded"
                            is: true
                    inputs:
                      - "annotate the sample lease"
                      - input: "annotate the two-tenant lease"
                        expected:
                          - in: doc
                            path: "$.tenants[*].name"
                            contains-set: ["Muster AG"]
                    """);
            var forms = declaration.criteria().get(0).forms();
            assertThat(forms).extracting(f -> f.form()).containsExactly(
                    PostconditionForm.EQUALS_SET, PostconditionForm.CONTAINS_SET,
                    PostconditionForm.COUNT_EQUALS, PostconditionForm.IS);
            assertThat(forms.get(3).argument()).isEqualTo(Boolean.TRUE);
        }

        @Test
        @DisplayName("a malformed numeric operand is refused")
        void valueOperandMalformed() {
            assertRefused(withQuoteForm("eq: \"twelve\""),
                    "`eq:` takes a number or a numeric string");
        }

        @Test
        @DisplayName("is-null takes the literal true and nothing else")
        void isNullOperand() {
            assertRefused(withQuoteForm("is-null: false"), "the negation is not offered");
        }

        @Test
        @DisplayName("is takes a boolean operand and nothing else")
        void isOperandNotBoolean() {
            assertRefused(withQuoteForm("is: \"true\""), "`is:` takes a boolean");
        }

        @Test
        @DisplayName("a set form without a declared view and path is refused")
        void setFormWithoutPath() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    transforms:
                      doc: json
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - in: doc
                            equals-set: ["a", "b"]
                    inputs: ["Alice"]
                    """, "requires `in:` naming a declared view and a `path:`");
        }

        @Test
        @DisplayName("a set form's operand lists at least one scalar element")
        void setOperandEmpty() {
            assertRefused(withQuoteForm("contains-set: []"),
                    "non-empty list of scalar values");
        }

        @Test
        @DisplayName("count-equals takes a non-negative integer")
        void countEqualsOperand() {
            assertRefused(withQuoteForm("count-equals: -1"),
                    "`count-equals:` takes a non-negative integer");
            assertRefused(withQuoteForm("count-equals: 2.5"),
                    "`count-equals:` takes a non-negative integer");
        }

        @Test
        @DisplayName("the intuitive-but-refused spellings name the intended form")
        void guidingRefusals() {
            assertRefused(MINIMAL.replace("contains: \"hello\"", "equals: true"),
                    "`is: true` / `is: false`");
            assertRefused(MINIMAL.replace("contains: \"hello\"", "equals: null"),
                    "a null expectation is `is-null: true`");
        }

        private static String withQuoteForm(String form) {
            return """
                    format: mavai-contract/1
                    contract: c
                    service: s
                    transforms:
                      doc: json
                    criteria:
                      - threshold: 0.9
                        postconditions:
                          - in: doc
                            path: "$.value"
                            %s
                    inputs: ["Alice"]
                    """.formatted(form);
        }
    }

    @Nested
    @DisplayName("transform refusals")
    class TransformRefusals {

        @Test
        @DisplayName("declaring the reserved raw view is refused")
        void rawViewDeclared() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    transforms:
                      raw: json
                    criteria: [{ threshold: 0.9, contains: "ok" }]
                    inputs: ["Alice"]
                    """, "reserved name of the untransformed response");
        }
    }

    @Nested
    @DisplayName("input refusals")
    class InputRefusals {

        @Test
        @DisplayName("an empty input list is refused")
        void inputsEmpty() {
            assertRefused(MINIMAL.replaceFirst("(?s)inputs:.*", "inputs: []\n"),
                    "`inputs:` must be a non-empty list");
        }

        @Test
        @DisplayName("a list mixing scalars and parts is refused")
        void mixedList() {
            assertRefused(MINIMAL.replace("- \"Alice\"", "- [\"a scalar\", { image: ./x.png }]"),
                    "not a mix");
        }

        @Test
        @DisplayName("an unknown input part is refused")
        void unknownPart() {
            assertRefused(MINIMAL.replace("- \"Alice\"", "- [{ video: ./clip.mp4 }]"),
                    "unknown input part `video:`");
        }

        @Test
        @DisplayName("an expectations entry is exactly input plus expected")
        void expectationEntryShape() {
            assertRefused(MINIMAL.replace("- \"Alice\"",
                    "- { input: \"Alice\", expected: { contains: \"ok\" }, note: extra }"),
                    "single-key mapping");
        }

        @Test
        @DisplayName("parses inside per-input expectations is refused — a criterion-level form")
        void parsesInExpected() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    transforms:
                      basket: json
                    criteria: [{ threshold: 0.9, contains: "ok" }]
                    inputs:
                      - input: "Alice"
                        expected:
                          - parses: basket
                    """, "criterion-level form");
        }

        @Test
        @DisplayName("per-input expectations require exactly one criterion")
        void expectationsRequireSingleCriterion() {
            assertRefused("""
                    format: mavai-contract/1
                    contract: c
                    service: s
                    criteria:
                      - { name: first, threshold: 0.9, contains: "ok" }
                      - { name: second, threshold: 0.8, matches: '\\w' }
                    inputs:
                      - input: "Alice"
                        expected: { contains: "ok" }
                    """, "exactly one criteria entry");
        }

        @Test
        @DisplayName("a file-sourced part with no disk location to resolve against is refused")
        void filePartWithoutBase() {
            assertRefused(MINIMAL.replace("- \"Alice\"", "- [{ audio: ./clip.m4a }]"),
                    "needs a contract loaded from disk");
        }

        @Test
        @DisplayName("an unreadable input file is refused at load")
        void unreadableFile(@TempDir Path directory) throws IOException {
            Path contract = directory.resolve("contract.yaml");
            Files.writeString(contract, MINIMAL.replace("- \"Alice\"", "- [{ audio: ./missing.m4a }]"));
            assertThatThrownBy(() -> ContractParser.load(contract))
                    .isInstanceOf(ContractConfigurationException.class)
                    .hasMessageContaining("cannot read input file");
        }
    }

    @Nested
    @DisplayName("latency refusals")
    class LatencyRefusals {

        private String withLatency(String block) {
            return MINIMAL.replace("criteria:", block + "criteria:");
        }

        @Test
        @DisplayName("contradictory shapes are refused")
        void shapeContradiction() {
            assertRefused(withLatency("latency:\n  p95: 500\n  empirical: [p99]\n"), "contradictory");
        }

        @Test
        @DisplayName("a bound-less block is refused")
        void withoutBounds() {
            assertRefused(withLatency("latency:\n  confidence: 0.9\n"), "declares no bounds");
        }

        @Test
        @DisplayName("a non-positive ceiling is refused")
        void ceilingNotPositive() {
            assertRefused(withLatency("latency:\n  p95: 0\n"),
                    "positive whole number of milliseconds");
        }

        @Test
        @DisplayName("an unknown percentile is refused")
        void percentileVocabulary() {
            assertRefused(withLatency("latency:\n  empirical: [p42]\n"), "unknown percentile");
        }

        @Test
        @DisplayName("decreasing ceilings are refused")
        void decreasingCeilings() {
            assertRefused(withLatency("latency:\n  p50: 1000\n  p95: 500\n"), "non-decreasing");
        }

        @Test
        @DisplayName("the latency provenance origin set is closed")
        void latencyOriginVocabulary() {
            assertRefused(withLatency("latency:\n  p95: 500\n  threshold-origin: contract\n"),
                    "provenance category");
        }
    }
}
