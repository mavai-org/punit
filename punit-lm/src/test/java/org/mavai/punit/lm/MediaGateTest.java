package org.mavai.punit.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.FileInput;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.decl.spi.MessageParts;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * The media capability gate: a media kind is carried when the
 * adapter's protocol can encode it and the service declared the
 * matching capability — an undeclared capability is never sent
 * silently, and the refusal fires at admission, before any sample.
 */
@DisplayName("the media capability gate")
class MediaGateTest {

    private final ServiceType type = new LanguageModelServiceType();
    private StubLlm stub;

    @BeforeEach
    void start() {
        stub = StubLlm.start();
        System.setProperty("mavai.llm.endpoint", stub.endpoint());
        System.setProperty("mavai.llm.api-key", "test-key");
    }

    @AfterEach
    void stop() {
        stub.close();
        System.clearProperty("mavai.llm.endpoint");
        System.clearProperty("mavai.llm.api-key");
    }

    private ConfiguredService service(Object... pairs) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("system-prompt", "You describe images.");
        configuration.put("model", "test-model");
        for (int i = 0; i < pairs.length; i += 2) {
            configuration.put((String) pairs[i], pairs[i + 1]);
        }
        return type.configure("svc", configuration);
    }

    private static FileInput part(MediaKind kind, String name) {
        return new FileInput(Path.of(name), kind, new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("an undeclared media capability is never sent silently")
    void undeclaredCapabilityRefused() {
        ConfiguredService service = service();
        assertThatThrownBy(() -> service.admit(part(MediaKind.IMAGE, "cat.png")))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining("has not allowed it")
                .hasMessageContaining("capabilities: [image-input]");
    }

    @Test
    @DisplayName("a kind the protocol cannot encode is refused at admission")
    void unencodableKindRefused() {
        ConfiguredService service = service("provider", "anthropic");
        assertThatThrownBy(() -> service.admit(part(MediaKind.AUDIO, "note.wav")))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining("cannot carry audio input")
                .hasMessageContaining("no content block for it");
    }

    @Test
    @DisplayName("the allowance widens assumptions, never the protocol — declaring the unencodable is refused")
    void unencodableDeclarationRefused() {
        // anthropic has no audio block, so the declaration itself is
        // already refused at configuration time.
        assertThatThrownBy(() -> service("provider", "anthropic",
                "capabilities", List.of("audio-input")))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining("cannot encode");
    }

    @Test
    @DisplayName("the file kind is deliver-to-binding only — no model wire form")
    void fileKindRefused() {
        ConfiguredService service = service("capabilities", List.of("image-input"));
        assertThatThrownBy(() -> service.admit(part(MediaKind.FILE, "data.csv")))
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining("hand the file to a bound service");
    }

    @Test
    @DisplayName("a declared, encodable kind is admitted and rides the wire as a data URI")
    void admittedImageRidesTheWire() {
        ConfiguredService service = service("capabilities", List.of("image-input"));
        MessageParts input = new MessageParts(
                List.of("what is this?", part(MediaKind.IMAGE, "cat.png")));
        assertThatCode(() -> service.admit(input)).doesNotThrowAnyException();
        stub.completeWith("a cat");
        service.invoke(input);
        var content = stub.lastRequest().get("messages").get(1).get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.get(0).get("type").asText()).isEqualTo("text");
        assertThat(content.get(1).get("image_url").get("url").asText())
                .startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("text-only input stays a plain string on the wire — byte-identical requests")
    void textOnlyStaysPlain() {
        ConfiguredService service = service();
        assertThatCode(() -> service.admit("plain question")).doesNotThrowAnyException();
        stub.completeWith("plain answer");
        service.invoke("plain question");
        assertThat(stub.lastRequest().get("messages").get(1).get("content").isTextual())
                .isTrue();
    }
}
