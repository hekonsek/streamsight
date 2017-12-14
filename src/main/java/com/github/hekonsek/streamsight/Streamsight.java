package com.github.hekonsek.streamsight;

import com.github.hekonsek.rxjava.connector.kafka.KafkaSource;
import com.github.hekonsek.rxjava.connector.slack.SlackTable;
import com.github.hekonsek.rxjava.view.document.DocumentView;
import com.github.hekonsek.rxjava.view.document.memory.InMemoryDocumentView;
import com.google.common.collect.ImmutableMap;
import io.debezium.kafka.KafkaCluster;
import io.reactivex.Observable;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.reactivex.core.Vertx;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.hekonsek.rxjava.connector.slack.SlackSource.slackSource;
import static com.github.hekonsek.rxjava.event.Headers.responseCallback;
import static com.github.hekonsek.rxjava.view.document.MaterializeDocumentViewTransformation.materialize;
import static io.vertx.core.json.Json.encodeToBuffer;
import static io.vertx.reactivex.core.Vertx.vertx;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.lang3.RandomUtils.nextInt;

public class Streamsight {

    public static void main(String[] args) {
        // Configuration
        String slackToken = System.getenv("SLACK_TOKEN");
        String slackChannel = System.getenv("SLACK_CHANNEL");

        // Services
        startEmbeddedKafka();
        Vertx vertx = vertx();
        DocumentView shadowView = new InMemoryDocumentView();

        // Pipes
        new KafkaSource<String, Map<String, Object>>(vertx, "metrics").build().
                compose(materialize(shadowView)).
                subscribe();
        slackSource(slackToken, slackChannel).build().
                subscribe(event -> {
                    List<List<Object>> metricsRows = stream(shadowView.findAll("metrics").blockingIterable().spliterator(), true).
                            map(document -> asList(document.key(), document.document().get("value"))).collect(toList());
                    SlackTable metricsTable = new SlackTable("Metrics:", asList("Metric", "Value"), metricsRows);
                    responseCallback(event).orElseThrow(() -> new IllegalStateException("No response callback found.")).
                            respond(metricsTable);
                });

        KafkaProducer producer = KafkaProducer.create(vertx.getDelegate(), ImmutableMap.of(
                "bootstrap.servers", "localhost:9092",
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", BytesSerializer.class.getName()
        ));
        String metricKey = "server1.cpu";
        Observable.interval(1, TimeUnit.SECONDS).
                subscribe(e -> producer.write(KafkaProducerRecord.create("metrics", metricKey, new Bytes(encodeToBuffer(
                        ImmutableMap.of("value", nextInt(0, 100), "timestamp", new Date())
                ).getBytes()))));
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