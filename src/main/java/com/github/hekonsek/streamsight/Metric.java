package com.github.hekonsek.streamsight;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class Metric<T> {

    private String key;

    private Date timestamp;

    private T value;

}