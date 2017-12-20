package com.github.hekonsek.streamsight;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.reactivex.core.buffer.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static io.vertx.reactivex.core.Vertx.vertx;
import static org.apache.commons.io.IOUtils.toByteArray;

@RunWith(VertxUnitRunner.class)
public class StreamsightTest {

    static {
        System.setProperty("TELEGRAF_PORT", "8087");
        new Streamsight();
    }

    @Test
    public void shouldSendBatch(TestContext context) throws IOException {
        Async async = context.async();
        vertx().createHttpClient().post(8087, "localhost", "/").handler(response -> {
            async.complete();
        }).end(Buffer.buffer(new String(toByteArray(getClass().getResourceAsStream("/line_protocol_example.txt")))));
    }

}
