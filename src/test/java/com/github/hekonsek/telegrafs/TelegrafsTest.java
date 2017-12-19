package com.github.hekonsek.telegrafs;

import org.junit.Test;

import java.io.IOException;

import static com.github.hekonsek.telegrafs.Telegrafs.parseLineProtocolRecords;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.assertj.core.api.Assertions.assertThat;

public class TelegrafsTest {

    @Test
    public void shouldParseBatch() throws IOException {
        byte[] lineProtocolBatch = toByteArray(getClass().getResourceAsStream("/line_protocol_example.txt"));
        long recordsCount = parseLineProtocolRecords(lineProtocolBatch).count().blockingGet();
        assertThat(recordsCount).isEqualTo(1000L);
    }

}
