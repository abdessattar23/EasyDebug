package io.github.abdessattar23.easydebug;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BodyCaptureTest {

    @Test
    void keepsFullHashWhenTheStoredPrefixIsCapped() {
        byte[] body = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);

        Payload payload = BodyCapture.capture(body, "text/plain", null, 4);

        assertThat(payload.truncated()).isTrue();
        assertThat(payload.size()).isEqualTo(body.length);
        assertThat(payload.body()).isEqualTo("abcd");
        assertThat(payload.bodyEncoding()).isEqualTo("text");
        assertThat(payload.sha256()).isEqualTo(BodyCapture.sha256(body));
    }

    @Test
    void readsFilenameFromContentDispositionAndFromMultipartBytes() {
        byte[] body = (
                "--x\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"annexe.xlsx\"\r\n"
                        + "\r\nPK").getBytes(StandardCharsets.ISO_8859_1);

        Payload fromHeader = BodyCapture.capture(new byte[]{1, 2, 3, 4}, "application/octet-stream",
                "attachment; filename=\"annexe.xlsx\"", 10);
        Payload fromBody = BodyCapture.capture(body, "multipart/form-data; boundary=x", null, body.length);

        assertThat(fromHeader.filename()).isEqualTo("annexe.xlsx");
        assertThat(fromHeader.bodyEncoding()).isEqualTo("base64");
        assertThat(fromBody.filename()).isEqualTo("annexe.xlsx");
        assertThat(fromBody.truncated()).isFalse();
        assertThat(fromBody.sha256()).isEqualTo(BodyCapture.sha256(body));
    }

    @Test
    void spoolSpillsToDiskWithoutLosingBytesOrTheHash() throws Exception {
        byte[] body = new byte[32];
        Arrays.fill(body, (byte) 7);
        Spool spool = new Spool(4, 8);
        spool.write(body);

        Payload payload = spool.toPayload("application/octet-stream", null);
        byte[] replayed = spool.open().readAllBytes();
        spool.close();

        assertThat(replayed).containsExactly(body);
        assertThat(payload.size()).isEqualTo(32);
        assertThat(payload.truncated()).isTrue();
        assertThat(payload.body()).isEqualTo(java.util.Base64.getEncoder().encodeToString(Arrays.copyOf(body, 4)));
        assertThat(payload.sha256()).isEqualTo(BodyCapture.sha256(body));
    }
}
