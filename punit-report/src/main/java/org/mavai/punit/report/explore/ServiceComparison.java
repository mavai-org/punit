package org.mavai.punit.report.explore;

import java.util.List;

/**
 * All Explore-experiment variants gathered for one service contract — the
 * unit the comparison report renders as a single section.
 *
 * @param service  the service contract identifier ({@code useCaseId})
 * @param variants the variants compared, in file-discovery order
 *                 (the renderer applies its own ranking)
 */
record ServiceComparison(String service, List<Variant> variants) {
}
