package com.github.hekonsek.streamsight;

import com.github.hekonsek.rxjava.connector.kafka.KafkaSource;
import com.google.common.collect.ImmutableMap;
import io.debezium.kafka.KafkaCluster;
import io.reactivex.Observable;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.reactivex.core.Vertx;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.io.FileUtils.deleteDirectory;

public class Streamsight {

    public static void main(String[] args) {
        // Services
        startEmbeddedKafka();
        Vertx vertx = Vertx.vertx();

        KafkaProducer producer = KafkaProducer.create(vertx.getDelegate(), ImmutableMap.of(
                "bootstrap.servers", "localhost:9092",
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", BytesSerializer.class.getName()
        ));
        Observable.interval(1, TimeUnit.SECONDS).subscribe( e -> producer.write(KafkaProducerRecord.create("metrics", "key", new Bytes(Json.encodeToBuffer(
                ImmutableMap.of("foo", "bar")
        ).getBytes()))));

        new KafkaSource<String, Map<String, Object>>(vertx, "metrics").build().
                subscribe(System.out::println);
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