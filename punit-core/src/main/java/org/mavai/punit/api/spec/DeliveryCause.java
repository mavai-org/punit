package org.mavai.punit.api.spec;

import org.mavai.outcome.Outcome;

/**
 * Why a delivery failed, from a closed vocabulary.
 *
 * <p>A failed <em>delivery</em> — the service unreachable, an error
 * status, a body carrying nothing to judge — is a failed
 * <em>sample</em>: counted against every criterion, exactly as it
 * always has been. This type changes nothing about that count. It
 * states what <em>kind</em> of failure it was, so a reader can tell a
 * run in which nothing was measured from a run in which everything was
 * measured and found wanting. Without it the two are indistinguishable
 * on every surface punit emits: a configuration whose endpoint was down
 * and a configuration whose answers were bad both read as a pass rate
 * of zero.
 *
 * <p><strong>No emitter states a cause it cannot know.</strong> The two
 * timeout senses are separate tokens deliberately.
 * {@link #CLIENT_DEADLINE} says <em>this framework stopped waiting</em>,
 * which only a caller holding a deadline of its own may claim;
 * {@link #PEER_TIMEOUT} says the peer stated that <em>it</em> did. The
 * same elapsed seconds, two different facts about who gave up — and a
 * single {@code timeout} token would have been a place for them to be
 * confused.
 *
 * <p>Authors writing their own bindings state a cause through
 * {@link #fail(String)}, which reserves a failure-id namespace for the
 * purpose. That namespace is what lets the emitters recognise a
 * delivery failure without guessing: a contract failure of the author's
 * own naming is never mistaken for one of these, however it is spelled.
 *
 * @see <a href="https://github.com/mavai-org/mavai-R">the family's
 *      interchange schemas, where this vocabulary is normative</a>
 */
public enum DeliveryCause {

    /** No response at all — name resolution, a refused connection. */
    UNREACHABLE("unreachable"),

    /** This framework's own stated deadline elapsed: it stopped waiting. */
    CLIENT_DEADLINE("client-deadline"),

    /** The peer stated that it timed out — a different fact from the above. */
    PEER_TIMEOUT("peer-timeout"),

    /** The service answered that it is failing. */
    SERVER_ERROR("server-error"),

    /** A delivered body carrying nothing to judge. */
    UNUSABLE_RESPONSE("unusable-response");

    /**
     * The reserved failure-id namespace carrying a delivery cause.
     *
     * <p>Reserved: an author's own contract failures must not use it.
     * The emitters read it as the assertion "this trial delivered
     * nothing", and a false claim there is worse than silence — it
     * would tell a reader the service was never reached when in truth
     * it answered and failed a check.
     */
    public static final String NAMESPACE = "mavai-delivery";

    private final String token;

    DeliveryCause(String token) {
        this.token = token;
    }

    /**
     * The wire token, as the family's interchange schemas spell it.
     * This is the delivery entry's identity in an emitted artefact —
     * bounded and groupable, which a message naming an endpoint is not.
     */
    public String token() {
        return token;
    }

    /**
     * A failed delivery on the {@code Outcome} channel: this cause as
     * the bounded identity, the given message as the text a reader
     * reads.
     *
     * <p>The two are deliberately separate. The message names the
     * endpoint, the provider, the status — everything needed to
     * diagnose, and none of it fit to group by or to put in an
     * artefact's identity field. Shortening the message to make it fit
     * would trade the diagnostic for the conformance and lose the part
     * the reader actually needs.
     */
    public <T> Outcome<T> fail(String message) {
        return Outcome.fail(NAMESPACE, token, message);
    }
}
