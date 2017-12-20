package com.github.hekonsek.telegrafs;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Function;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;
import static java.lang.Long.parseLong;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class Telegrafs {

    public static Observable<LineProtocolRecord> parseLineProtocolRecords(byte[] lineProtocolBatch) {
        return Observable.fromIterable(stream(new String(lineProtocolBatch).split("\n")).
                map(line -> {
                    String measurementAndTagsSegment = line.replaceFirst(" .*", "");
                    String fieldsSegment = line.replace(measurementAndTagsSegment + " ", "").replaceFirst(" .*", "");
                    String timestampSegment = line.replaceFirst("^.* ", "");

                    String[] measurementAndTags = measurementAndTagsSegment.split(",", 2);
                    String measurement = measurementAndTags[0];
                    Map<String, String> tags = stream(measurementAndTags[1].split(",")).
                            collect(Collectors.toMap(tag -> tag.split("=")[0], tag -> tag.split("=")[1]));
                    Map<String, String> fields = stream(fieldsSegment.split(",")).
                            collect(Collectors.toMap(tag -> tag.split("=")[0], tag -> tag.split("=")[1]));
                    Date timestamp = new Date(parseLong(timestampSegment) / 1000L / 1000L);
                    return new LineProtocolRecord(measurement, tags, fields, timestamp);
                }).collect(toList()));
    }

}