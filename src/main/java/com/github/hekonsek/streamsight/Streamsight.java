package com.github.hekonsek.streamsight;

import com.github.hekonsek.rxjava.connector.http.HttpSourceFactory;
import com.github.hekonsek.rxjava.connector.kafka.KafkaSource;
import com.github.hekonsek.rxjava.connector.slack.SlackTable;
import com.github.hekonsek.rxjava.view.document.DocumentView;
import com.github.hekonsek.rxjava.view.document.memory.InMemoryDocumentView;
import com.google.common.collect.ImmutableMap;
import io.debezium.kafka.KafkaCluster;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.reactivex.core.Vertx;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.github.hekonsek.rxjava.connector.slack.SlackSource.slackSource;
import static com.github.hekonsek.rxjava.event.Headers.responseCallback;
import static com.github.hekonsek.rxjava.view.document.MaterializeDocumentViewTransformation.materialize;
import static com.github.hekonsek.telegrafs.Telegrafs.activeTotalCpu;
import static io.vertx.core.json.Json.encodeToBuffer;
import static io.vertx.reactivex.core.Vertx.vertx;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.io.FileUtils.deleteDirectory;

public class Streamsight {

    public static void main(String[] args) {
        // Configuration
        String slackToken = System.getenv("SLACK_TOKEN");
        String slackChannel = System.getenv("SLACK_CHANNEL");

        // Services
        startEmbeddedKafka();
        Vertx vertx = vertx();
        DocumentView shadowView = new InMemoryDocumentView();

        // Telegraf pipe
        KafkaProducer producer = KafkaProducer.create(vertx.getDelegate(), ImmutableMap.of(
                "bootstrap.servers", "localhost:9092",
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", BytesSerializer.class.getName()
        ));
        vertx.createHttpServer().requestHandler(request ->
                request.bodyHandler(body -> {
                    activeTotalCpu(body.getDelegate().getBytes()).subscribe(metric ->
                            producer.write(KafkaProducerRecord.create("metrics", "localhost.cpu", new Bytes(encodeToBuffer(
                                    new Metric<>("localhost.cpu", metric.getKey(), metric.getValue())
                            ).getBytes())))
                    );
                    request.response().setStatusCode(204).end("OK");
                })
        ).listen(8086);

        // Kafka metrics pipe
        new KafkaSource<String, Map<String, Object>>(vertx, "metrics").build().
                compose(materialize(shadowView)).
                subscribe();

        // Slack bot pipe
        slackSource(slackToken, slackChannel).build().
                subscribe(event -> {
                    List<List<Object>> metricsRows = stream(shadowView.findAll("metrics").blockingIterable().spliterator(), true).
                            map(document -> asList(document.key(), document.document().get("value"))).collect(toList());
                    SlackTable metricsTable = new SlackTable("Metrics:", asList("Metric", "Value"), metricsRows);
                    responseCallback(event).orElseThrow(() -> new IllegalStateException("No response callback found.")).
                            respond(metricsTable);
                });
    }

    private static void startEmbeddedKafka() {
        try {
            File debeziumDirectory = new File("/var/streamsight/debezium");
            deleteDirectory(new File(debeziumDirectory, "zk"));
            new KafkaCluster().addBrokers(1).
                    withPorts(2181, 9092).
                    usingDirectory(debeziumDirectory).
                    startup();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}