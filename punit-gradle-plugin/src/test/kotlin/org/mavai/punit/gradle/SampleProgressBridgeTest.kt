package org.mavai.punit.gradle

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SampleProgressBridge")
class SampleProgressBridgeTest {

    private val captured = ByteArrayOutputStream()
    private val bridge = PUnitPlugin.SampleProgressBridge(PrintStream(captured, true, Charsets.UTF_8))

    private fun relayed(vararg chunks: String): String {
        chunks.forEach(bridge::relay)
        return captured.toString(Charsets.UTF_8)
    }

    @Test
    @DisplayName("consecutive progress chunks update one line in place")
    fun progressUpdatesInPlace() {
        assertEquals("\r1/3\r2/3\r3/3",
            relayed("[PUNIT-PROGRESS]1/3\n", "[PUNIT-PROGRESS]2/3\n", "[PUNIT-PROGRESS]3/3\n"))
    }

    @Test
    @DisplayName("output after a counter starts on a line of its own, the last count kept")
    fun outputAfterCounterStartsOnItsOwnLine() {
        assertEquals("\r20/20\n[PUNIT] baseline recorded: /tmp/b.yaml\n",
            relayed("[PUNIT-PROGRESS]20/20\n", "[PUNIT] baseline recorded: /tmp/b.yaml\n"))
    }

    @Test
    @DisplayName("a failure line mid-run ends the counter line, and counting resumes below it")
    fun failureLineMidRun() {
        assertEquals("\r8/20\n[PUNIT-FAIL] sample 8\n\r9/20",
            relayed("[PUNIT-PROGRESS]8/20\n", "[PUNIT-FAIL] sample 8\n", "[PUNIT-PROGRESS]9/20\n"))
    }

    @Test
    @DisplayName("text before the marker in the same chunk is ended off an open counter too")
    fun mixedChunk() {
        assertEquals("\r1/2\nnote\n\r2/2",
            relayed("[PUNIT-PROGRESS]1/2\n", "note\n[PUNIT-PROGRESS]2/2\n"))
    }

    @Test
    @DisplayName("a chunk that already starts with a newline gains no second one")
    fun leadingNewlineNotDoubled() {
        assertEquals("\r1/1\ndone\n", relayed("[PUNIT-PROGRESS]1/1", "\ndone\n"))
    }

    @Test
    @DisplayName("output with no counter before it passes through unchanged")
    fun plainOutputUnchanged() {
        assertEquals("hello\nworld\n", relayed("hello\n", "world\n"))
    }
}
