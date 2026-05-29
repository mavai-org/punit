package org.mavai.punit.internal.engine.baseline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.BaselineLookup;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.BaselineStatistics;

/**
 * Adapts a {@link BaselineResolver} (file-system / YAML-aware) to the
 * {@link BaselineProvider} interface the spec layer programs against.
 * The fingerprint computation lives in {@link FactorsFingerprint} so
 * the read-side and the write-side use the same algorithm by
 * construction.
 */
public final class YamlBaselineProvider implements BaselineProvider {

    private final BaselineResolver resolver;

    public YamlBaselineProvider(Path baselineDir) {
        this(new BaselineResolver(Objects.requireNonNull(baselineDir, "baselineDir")));
    }

    public YamlBaselineProvider(BaselineResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public <S extends BaselineStatistics> Optional<S> baselineFor(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        String fingerprint = FactorsFingerprint.of(factors);
        return resolver.resolve(serviceContractId, fingerprint, criterionName, statisticsType,
                currentProfile, declarations);
    }

    @Override
    public Optional<String> baselineInputsIdentityFor(
            String serviceContractId, FactorBundle factors,
            CovariateProfile currentProfile, List<Covariate> declarations) {
        String fingerprint = FactorsFingerprint.of(factors);
        return resolver.resolveInputsIdentity(serviceContractId, fingerprint,
                currentProfile, declarations);
    }

    @Override
    public <S extends BaselineStatistics> BaselineLookup<S> baselineLookup(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        String fingerprint = FactorsFingerprint.of(factors);
        return resolver.lookup(serviceContractId, fingerprint, criterionName,
                statisticsType, currentProfile, declarations);
    }
}
