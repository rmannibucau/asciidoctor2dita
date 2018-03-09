package com.github.rmannibucau.asciidoctor.backend;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class Aggregator {

    @Setter
    private File images;

    private final Asciidoctor asciidoctor;

    private final OptionsBuilder optionsBuilder;

    private final Map<String, String> documents = new ConcurrentHashMap<>();

    private final Collection<File> resources = new ArrayList<>();

    public boolean fileExists(final String link) {
        return documents.containsKey(link);
    }
}
