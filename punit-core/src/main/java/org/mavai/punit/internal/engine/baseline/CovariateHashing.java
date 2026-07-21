package org.mavai.punit.internal.engine.baseline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.internal.util.HashUtils;

/**
 * Per-covariate-value hashing for the baseline filename schema.
 *
 * <p>Each covariate the service contract declared contributes one 4-character
 * hex hash to the baseline filename. The hash is computed as
 *
 * <pre>{@code
 * first4(sha256("{key}={canonical_value}"))
 * }</pre>
 *
 * with the following normative pieces:
 *
 * <ul>
 *   <li>{@code {key}} is the covariate name exactly as
 *       {@link Covariate#name()}
 *       returns it (snake_case for built-ins).</li>
 *   <li>{@code {canonical_value}} is the resolved label produced by
 *       the {@code CovariateResolver}. For partitioned built-ins this
 *       is the partition label; for {@code TimezoneCovariate} it is
 *       the IANA string; for {@code CustomCovariate} it is whatever
 *       the developer-supplied resolver returned.</li>
 *   <li>The separator is exactly {@code "="} with no whitespace.</li>
 *   <li>The hash input is UTF-8 bytes.</li>
 *   <li>The truncation is the first 4 hex characters of the SHA-256
 *       digest, matching the other 4-character hash components in the
 *       baseline filename schema.</li>
 * </ul>
 *
 * <p>Hashes appear in the filename in the order the covariates were
 * declared by {@code ServiceContract.covariates()}; {@link CovariateProfile}
 * preserves that order through resolution and round-trip, so the
 * implementation here just iterates the profile's value map.
 */
// mavai-ref: JVI-07HPCY* — do not remove (resolves in mavai-orchestrator)
final class CovariateHashing {

    static final int HASH_LENGTH = 4;

    private CovariateHashing() { }

    /**
     * @return one 4-character hex hash per entry in {@code profile},
     *         in the profile's iteration (i.e. declaration) order;
     *         empty list when the profile is empty
     */
    static List<String> hashesFor(CovariateProfile profile) {
        if (profile.isEmpty()) {
            return List.of();
        }
        List<String> hashes = new ArrayList<>(profile.size());
        for (Map.Entry<String, String> entry : profile.values().entrySet()) {
            hashes.add(hashOne(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(hashes);
    }

    static String hashOne(String key, String canonicalValue) {
        String input = key + "=" + canonicalValue;
        return HashUtils.truncateHash(HashUtils.sha256(input), HASH_LENGTH);
    }
}
