package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FailureCount + FailureExemplar")
class FailureCountTest {

    @Test
    @DisplayName("empty() carries zero count and an empty exemplar list")
    void emptyBucket() {
        FailureCount bucket = FailureCount.empty();
        assertThat(bucket.count()).isZero();
        assertThat(bucket.exemplars()).isEmpty();
    }

    @Test
    @DisplayName("exemplars are defensively copied")
    void exemplarsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<FailureExemplar>();
        mutable.add(new FailureExemplar("a", "tripped"));

        FailureCount bucket = new FailureCount(1, mutable);
        mutable.add(new FailureExemplar("b", "intruder"));

        assertThat(bucket.exemplars()).hasSize(1);
    }

    @Test
    @DisplayName("count must be non-negative")
    void rejectsNegativeCount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FailureCount(-1, List.of()));
    }

    @Test
    @DisplayName("exemplars cannot exceed count")
    void rejectsTooManyExemplars() {
        var two = List.of(
                new FailureExemplar("a", "x"),
                new FailureExemplar("b", "y"));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FailureCount(1, two));
    }

    @Test
    @DisplayName("FailureExemplar rejects null fields")
    void exemplarRejectsNulls() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new FailureExemplar(null, "reason"));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new FailureExemplar("input", null));
    }

    @Test
    @DisplayName("exemplars list is immutable after construction")
    void exemplarsImmutable() {
        FailureCount bucket = new FailureCount(1, List.of(new FailureExemplar("x", "y")));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> bucket.exemplars().add(new FailureExemplar("z", "w")));
    }
}
