package com.github.rmannibucau.asciidoctor.backend.dita;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.List;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;

import com.github.rmannibucau.asciidoctor.backend.DocumentVisitor;

public class DitaVisitor implements DocumentVisitor {

    private final Collection<String> ids = new HashSet<>();

    private boolean inSection = false;
    private boolean inTable = false;

    @Override
    public String onDocument(final Document document, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        if ("table".equals(transform) || inTable) {
            return contentSupplier.get();
        }

        ids.clear();

        final String title = document.getDoctitle();
        final String id = extractId(document, title);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n" + "<concept id=\"" + id
                + "\" xml:lang=\"en\">" + "<title>" + title + "</title>" + "<conbody>" + sanitize(contentSupplier.get())
                + "</conbody>" + "</concept>";
    }

    @Override
    public String onSection(final Section section, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        final String title = section.getTitle();
        final String id = extractId(section, null);
        final boolean wasInSection = inSection;
        if (!wasInSection) {
            inSection = true;
        }
        final String tag = wasInSection ? "sectiondiv" : "section";
        final String titleTag = wasInSection ? "b" : "title";
        try {
            return "<" + tag + ofNullable(id).map(i -> " id=\"" + id + "\"").orElse("") + ">"
                    + ofNullable(title).map(t -> "<" + titleTag + ">" + t + "</" + titleTag + ">\n").orElse("")
                    + contentSupplier.get() + "</" + tag + ">\n";
        } finally {
            if (!wasInSection) {
                inSection = wasInSection;
            }
        }
    }

    @Override
    public String onListing(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        return "<codeblock>" + xmlEscape(contentSupplier.get()) + "</codeblock>\n";
    }

    @Override
    public String onPreamble(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        return "<abstract>" + contentSupplier.get() + "</abstract>\n";
    }

    @Override
    public String onParagraph(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        if (block.getBlocks().isEmpty()) {
            if (inTable) {
                return contentSupplier.get();
            }
            return "<p>" + contentSupplier.get() + "</p>\n";
        }
        return contentSupplier.get();
    }

    @Override
    public String onImage(final Block block, final String transform, final Map<Object, Object> opts, final String alt,
            final String path) {
        final String id = extractId(block, path.replace("/", "_").replace(".", "_"));
        return "<fig id=\"fig_" + id + "\">" + "<image href=\"" + path + "\" id=\"image_" + id + "\" />" + "</fig>";
    }

    @Override
    public String onAdmonition(final Block block, final String transform, final Map<Object, Object> opts, final String label,
            final Supplier<String> contentSupplier) {
        return "<note type=\"" + toNoteType(label) + "\">" + contentSupplier.get() + "</note>\n";
    }

    @Override
    public String onTable(final Table table, final String transform, final Map<Object, Object> opts,
            final Function<Cell, String> cellConverter) {
        // todo: add relcolwidth from adoc meta
        final boolean wasInTable = inTable;
        if (!wasInTable) {
            inTable = true;
        }
        try {
            return "<simpletable frame=\"all\">" + (table.getHeader()
                                                         .isEmpty() ? "" : convertTableRows(table.getHeader(),
                    cellConverter, "sthead").collect(joining("\n"))) + "\n" + convertTableRows(table.getBody(),
                    cellConverter, "strow").collect(joining("\n")) + "\n" + (table.getFooter()
                                                                                  .isEmpty() ? "" : convertTableRows(
                    table.getFooter(), cellConverter, "strow").collect(joining("\n"))) + "</simpletable>\n";
        } finally {
            if (!wasInTable) {
                inTable = false;
            }
        }
    }

    @Override
    public String onList(final List list, final String transform, final Map<Object, Object> opts) {
        return list.getItems().stream().map(ListItem.class::cast).map(this::onListItem)
                .collect(joining("\n", "<ul>\n", "</ul>\n"));
    }

    @Override
    public String onListItem(final ListItem item) {
        return "<li>" + item.getText() + "</li>";
    }

    @Override
    public String onMonospaced(final String value) {
        return "<codeph>" + value + "</codeph>";
    }

    @Override
    public String onXref(final String value, final String title) {
        return "<xref href=\"" + title.replace(".adoc", ".dita") + "\">" + value + "</xref>";
    }

    @Override
    public String onStrong(final String value) {
        return "<b>" + value + "</b>";
    }

    @Override
    public String onEmphasis(final String value) {
        return "<emphasis role=\"italic\">" + value + "</emphasis>";
    }

    private Stream<String> convertTableRows(final java.util.List<Row> rows, final Function<Cell, String> cellConverter,
            final String rowMarker) {
        return rows.stream().map(row -> onRow(row, cellConverter, rowMarker)).filter(l -> !l.isEmpty());
    }

    private String onRow(final Row row, final Function<Cell, String> cellConverter, final String rowMarker) {
        return "<" + rowMarker + ">" + row.getCells().stream().map(cellConverter)
                .collect(joining("</stentry>\n<stentry>", "<stentry>", "</stentry>\n")) + "</" + rowMarker + ">";
    }

    private String extractId(final StructuralNode document, final String title) {
        String idBase = ofNullable(document.getId())
                .orElseGet(() -> ofNullable(title).map(t -> t.replace(" ", "_")).orElse(null));
        int i = 1;
        while (!ids.add(idBase)) {
            idBase += i++;
        }
        return idBase;
    }

    private String xmlEscape(final String content) {
        return StringEscapeUtils.escapeXml11(content);
    }

    private String toNoteType(final String label) {
        final String type = label.toLowerCase(Locale.ROOT);
        switch (type) {
        case "note":
        case "tip":
        case "warning":
            return type;
        default:
            return "note";
        }
    }

    private String sanitize(final String content) {
        return content.replace("<<", "").replace(">>", "");
    }
}
