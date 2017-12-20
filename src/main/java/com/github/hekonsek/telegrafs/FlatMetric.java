package com.github.hekonsek.telegrafs;

import java.util.Date;

public class FlatMetric<T> {

    private final String key;

    private final Date timestamp;

    private final T value;

    public FlatMetric(String key, Date timestamp, T value) {
        this.key = key;
        this.timestamp = timestamp;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public T getValue() {
        return value;
    }

}