package com.github.rmannibucau.asciidoctor.backend;

import java.io.File;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.OptionsBuilder;

public class DitaService {
    public static void main(final String[] args) {
        final Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        final OptionsBuilder options = OptionsBuilder.options().toFile(false).backend("dita")
                .attributes(AttributesBuilder.attributes().attribute("preambleAsParagraph", "true"));
        System.out.println(asciidoctor.renderFile(new File(args[0]), options));
    }
}
