package org.mavai.punit.decl;

/**
 * A declarative-layer refusal: a malformed file, a reserved construct,
 * an unresolvable name, a contradiction — always raised at load time,
 * before any sample runs.
 *
 * <p>Travels on punit's defect channel ({@link IllegalStateException}
 * and kin), surfacing as a test <em>error</em>, never a FAIL: a
 * configuration defect is a bug to fix, not evidence about the service
 * under test. Messages speak the author's vocabulary — the file, the
 * YAML path, the known alternatives — and raw parser exceptions are
 * never the user-facing surface.
 */
public class ContractConfigurationException extends IllegalStateException {

    public ContractConfigurationException(String message) {
        super(message);
    }

    public ContractConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
