# Vendored JSONPath compliance test suite

`cts.json` is a vendored copy of the official JSONPath compliance test
suite published by the jsonpath-standard project
(github.com/jsonpath-standard/jsonpath-compliance-test-suite), snapshot
taken 2026-07-26 from the `main` branch (703 tests). It drives
`JsonPathComplianceTest` against punit-decl's own RFC 9535 engine.

Vendored, not fetched: the build passes offline. Refresh by replacing
`cts.json` with the current upstream `cts.json` and updating this note;
new upstream cases that fail land in the test's known-failures list
(visible, both-ways-asserted) until the engine catches up.
