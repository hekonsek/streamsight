package com.github.hekonsek.streamsight;

import io.debezium.kafka.KafkaCluster;

import java.io.File;
import java.io.IOException;

import static org.apache.commons.io.FileUtils.deleteDirectory;

public class Streamsight {

    public static void main(String[] args) throws IOException {
        File debeziumDirectory = new File("/var/streamsight/debezium");
        deleteDirectory(new File(debeziumDirectory, "zk"));
        new KafkaCluster().addBrokers(1).
                withPorts(9092, 2181).
                usingDirectory(debeziumDirectory).
                startup();
    }

}