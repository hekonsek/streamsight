package com.github.hekonsek.telegrafs;

import io.reactivex.Observable;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Date;
import java.util.List;

import static java.lang.Double.parseDouble;
import static java.lang.Long.parseLong;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class Telegrafs {

    public static Observable<Pair<Date, Double>> activeTotalCpu(byte[] telegrafBatch) {
        List<String[]> telegrafRecords = stream(new String(telegrafBatch).split("\n")).
                filter(line -> line.startsWith("cpu,")).map(line -> line.split(" ")).
                filter(segment -> segment[0].contains("cpu=cpu-total")).
                collect(toList());
        List<Pair<Date, Double>> metrics = telegrafRecords.stream().
                map(segment -> {
                    double usage = parseDouble(
                            stream(segment[1].split(",")).
                                    filter(metric -> metric.startsWith("usage_active=")).
                                    findFirst().get().replaceFirst("usage_active=", "")
                    );
                    return ImmutablePair.of(new Date(parseLong(segment[2]) / 1000L / 1000L), usage);
                }).
                collect(toList());
        return Observable.fromIterable(metrics);
    }

}