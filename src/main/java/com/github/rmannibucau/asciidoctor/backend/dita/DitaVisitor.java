package com.github.rmannibucau.asciidoctor.backend.dita;

import static java.util.stream.Collectors.joining;

import java.util.Map;
import java.util.function.Function;

import com.github.rmannibucau.asciidoctor.backend.DocumentVisitor;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.List;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.Table;

public class DitaVisitor implements DocumentVisitor {
    @Override
    public String onDocument(final Document document, final String transform, final Map<Object, Object> opts,
                             final String content) {
        final String title = document.getDoctitle();
        return ("table".equalsIgnoreCase(transform) ? "" : "= " + title + "\n\n") + content;
    }

    @Override
    public String onSection(final Section section, final String transform, final Map<Object, Object> opts,
                            final String content) {
        final String title = section.getTitle();
        return "== " + title + "\n" + content + "\n";
    }

    @Override
    public String onListing(final Block block, final String transform, final Map<Object, Object> opts,
                            final String content) {
        return "{code}\n" + content + "\n{code}\n";
    }

    @Override
    public String onPreamble(final Block block, final String transform, final Map<Object, Object> opts,
                             final String content) {
        return "{preamble}\n" + content + "\n{preamble}\n";
    }

    @Override
    public String onParagraph(final Block block, final String transform, final Map<Object, Object> opts,
                              final String content) {
        return "{paragraph}\n" + content + "\n{paragraph}\n";
    }

    @Override
    public String onImage(final Block block, final String transform, final Map<Object, Object> opts,
                          final String alt, final String path) {
        return "{image:" + alt + "}\n" + path + "\n{image}\n";
    }

    @Override
    public String onAdmonition(final Block block, final String transform, final Map<Object, Object> opts,
                               final String label, final String content) {
        return "{admonition:" + label + "}\n" + content + "\n{admonition}\n";
    }

    @Override
    public String onListItem(final ListItem item) {
        return item.getMarker() + " " + item.getText();
    }

    @Override
    public String onList(final List list, final String transform, final Map<Object, Object> opts) {
        return list.getItems().stream().map(ListItem.class::cast).map(this::onListItem).collect(joining("\n"));
    }

    @Override
    public String onMonospaced(final String value) {
        return "`" + value + "`";
    }

    @Override
    public String onStrong(final String value) {
        return "`" + value + "`";
    }

    @Override
    public String onEmphasis(final String value) {
        return "`" + value + "`";
    }

    @Override
    public String onTable(final Table table, final String transform, final Map<Object, Object> opts,
                          final Function<Cell, String> cellConverter) {
        return convertTableRows(table.getHeader(), cellConverter) + convertTableRows(table.getBody(), cellConverter) + convertTableRows(table.getFooter(), cellConverter);
    }

    private String convertTableRows(final java.util.List<Row> rows, final Function<Cell, String> cellConverter) {
        return rows.stream()
                .map(row -> onRow(row, cellConverter))
                .filter(l -> !l.isEmpty())
                .collect(joining("\n"));
    }

    private String onRow(final Row row, final Function<Cell, String> cellConverter) {
        return row.getCells().stream()
                .map(cellConverter)
                .collect(joining("|", "|", "\n"));
    }
}
