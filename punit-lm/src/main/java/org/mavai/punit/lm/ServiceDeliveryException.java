package org.mavai.punit.lm;

/**
 * The service failed to deliver a usable response — a server-side
 * error, an unreachable endpoint, or a delivered body not matching the
 * vendor's shape. A failed delivery is a failed <em>sample</em> (the
 * service did not deliver), counted against the criteria with its
 * cause as the reason — never an abort. Contrast
 * {@link ProviderResponseException}: a provider <em>rejection</em>
 * says the request was wrong, which is a defect.
 *
 * <p>Internal signalling only: the invocation boundary converts this
 * to an {@code Outcome} failure before it leaves the module.
 */
class ServiceDeliveryException extends RuntimeException {

    ServiceDeliveryException(String message) {
        super(message);
    }
}
