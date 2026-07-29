package org.mavai.punit.lm;

/**
 * The provider rejected the request — a defect, never a sample. A
 * client-side error status (a rejected schema, an unknown model id, an
 * expired credential) says the <em>request</em> was wrong, not that
 * the service is stochastic — counting it as failed samples would
 * render a verdict about nothing. The abort must be investigable, so
 * this carries the provider, the status, and the response body instead
 * of a bare status line.
 */
class ProviderResponseException extends IllegalStateException {

    ProviderResponseException(String provider, int status, String detail) {
        super("provider '" + provider + "' returned HTTP " + status + " — a defect, not a "
                + "sample; the run is aborted. The provider said: "
                + (detail == null || detail.isEmpty() ? "(empty body)" : detail));
    }
}
