package com.github.hekonsek.telegrafs;

import groovy.lang.GroovyShell;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Function;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LineProtocolRecordMapper implements Function<LineProtocolRecord, ObservableSource<FlatMetric>> {

    private final Map<String, String> rules;

    public LineProtocolRecordMapper(Map<String, String> rules) {
        this.rules = rules;
    }

    @Override public ObservableSource<FlatMetric> apply(LineProtocolRecord lineProtocolRecord) {
        GroovyShell groovy = groovy(lineProtocolRecord);
        List<FlatMetric> matching = rules.entrySet().stream().
                filter( entry -> (boolean) groovy.evaluate(entry.getKey())).
                flatMap(entry -> ((List<FlatMetric>) groovy.evaluate(entry.getValue())).stream()).
                collect(Collectors.toList());
        if(matching.isEmpty()) {
            return Observable.empty();
        } else {
            return Observable.fromIterable(matching);
        }
    }

    private GroovyShell groovy(LineProtocolRecord record) {
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addStaticStars(FlatMetric.class.getName());
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(importCustomizer);
        GroovyShell shell = new GroovyShell(configuration);
        shell.setVariable("record", record);
        return shell;
    }

}