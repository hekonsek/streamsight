package com.github.hekonsek.telegrafs;

import java.util.Date;
import java.util.Map;

public class LineProtocolRecord {

    private final String measurement;

    private final Map<String, String> tags;

    private final Map<String, String> fields;

    private final Date timestamp;

    public LineProtocolRecord(String measurement, Map<String, String> tags, Map<String, String> fields, Date timestamp) {
        this.measurement = measurement;
        this.tags = tags;
        this.fields = fields;
        this.timestamp = timestamp;
    }

    public String getMeasurement() {
        return measurement;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public Date getTimestamp() {
        return timestamp;
    }

}