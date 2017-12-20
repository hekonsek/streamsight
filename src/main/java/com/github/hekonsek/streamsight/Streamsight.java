package com.github.hekonsek.streamsight;

import com.github.hekonsek.rxjava.connector.kafka.KafkaSource;
import com.github.hekonsek.rxjava.connector.slack.SlackTable;
import com.github.hekonsek.rxjava.view.document.DocumentView;
import com.github.hekonsek.rxjava.view.document.memory.InMemoryDocumentView;
import com.github.hekonsek.telegrafs.LineProtocolRecordMapper;
import com.google.common.collect.ImmutableMap;
import io.debezium.kafka.KafkaCluster;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.kafka.client.producer.KafkaProducer;
import io.vertx.reactivex.kafka.client.producer.KafkaProducerRecord;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.github.hekonsek.rxjava.connector.slack.SlackSource.slackSource;
import static com.github.hekonsek.rxjava.event.Headers.responseCallback;
import static com.github.hekonsek.rxjava.view.document.MaterializeDocumentViewTransformation.materialize;
import static com.github.hekonsek.telegrafs.Telegrafs.parseLineProtocolRecords;
import static io.vertx.core.json.Json.encodeToBuffer;
import static io.vertx.reactivex.core.RxHelper.scheduler;
import static io.vertx.reactivex.core.Vertx.vertx;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.slf4j.LoggerFactory.getLogger;

public class Streamsight {

    private final static Logger log = getLogger(Streamsight.class);

    public Streamsight() {
        // Configuration
        String slackToken = System.getenv("SLACK_TOKEN");
        if (slackToken == null) {
            slackToken = System.getProperty("SLACK_TOKEN");
        }
        String slackChannel = System.getenv("SLACK_CHANNEL");
        if (slackChannel == null) {
            slackChannel = System.getProperty("SLACK_CHANNEL");
        }
        Integer telegrafPort = System.getenv("TELEGRAF_PORT") != null ? Integer.parseInt(System.getenv("TELEGRAF_PORT")) : null;
        if (telegrafPort == null) {
            telegrafPort = Integer.parseInt(System.getProperty("TELEGRAF_PORT", "8086"));
        }

        // Services
        startEmbeddedKafka();
        Vertx vertx = vertx();
        DocumentView shadowView = new InMemoryDocumentView();

        // Telegraf pipe
        KafkaProducer producer = KafkaProducer.create(vertx, ImmutableMap.of(
                "bootstrap.servers", "localhost:9092",
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", BytesSerializer.class.getName()
        ));
        vertx.createHttpServer().requestHandler(request ->
                request.bodyHandler(body -> {
                    if(log.isDebugEnabled()) {
                        log.debug("Received batch of line protocol event: {}", new String(body.getDelegate().getBytes()));
                    }
                    parseLineProtocolRecords(body.getDelegate().getBytes()).
                            observeOn(scheduler(vertx)).
                            subscribeOn(scheduler(vertx)).
                            flatMap(new LineProtocolRecordMapper(ImmutableMap.of(
                                    "record.tags.cpu == 'cpu-total'",
                                    "[metric(\"${record.tags.host}.cpu.usage_active\", record.timestamp, record.fields.usage_active)]",
                                    "record.measurement == 'diskio'",
                                    "[metric(\"${record.tags.host}.diskio.${record.tags.name}.io_time\", record.timestamp, record.fields.io_time)]",
                                    "record.measurement == 'mem'",
                                    "[metric(\"${record.tags.host}.mem.available_percent\", record.timestamp, record.fields.available_percent)]"
                            ))).
                            subscribe(metric ->
                                    producer.write(KafkaProducerRecord.create("metrics", metric.getKey(), new Bytes(encodeToBuffer(
                                            new Metric<>(metric.getKey(), metric.getTimestamp(), metric.getValue())
                                    ).getBytes())))
                            );
                    request.response().setStatusCode(204).end("OK");
                })
        ).listen(telegrafPort);

        // Kafka metrics pipe
        new KafkaSource<String, Map<String, Object>>(vertx, "metrics").build().
                compose(materialize(shadowView)).
                subscribe();

        // Slack bot pipe
        slackSource(slackToken, slackChannel).build().
                subscribe(event -> {
                    String command = event.payload().text();
                    if (command.equals("metrics")) {
                        List<List<Object>> metricsRows = stream(shadowView.findAll("metrics").blockingIterable().spliterator(), true).
                                map(document -> asList(document.key(), document.document().get("value"))).collect(toList());
                        SlackTable metricsTable = new SlackTable("Metrics:", asList("Metric", "Value"), metricsRows);
                        responseCallback(event).orElseThrow(() -> new IllegalStateException("No response callback found.")).
                                respond(metricsTable);
                    } else if (command.startsWith("metrics clear ")) {
                        String metricKey = command.replaceFirst("metrics clear ", "");
                        producer.rxWrite(KafkaProducerRecord.create("metrics", metricKey, null)).doOnEvent((metadata, value) ->
                                responseCallback(event).orElseThrow(() -> new IllegalStateException("No response callback found.")).
                                        respond(String.format("Metric %s has been cleared. :)", metricKey))
                        ).subscribe();
                    } else {
                        responseCallback(event).orElseThrow(() -> new IllegalStateException("No response callback found.")).
                                respond("Sorry, I can't recognize this command. :(");
                    }
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

    public static void main(String[] args) {
        new Streamsight();
    }

}