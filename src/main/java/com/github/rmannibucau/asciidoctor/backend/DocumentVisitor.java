package com.github.rmannibucau.asciidoctor.backend;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.List;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.Table;

public interface DocumentVisitor {
    String onDocument(Document document, String transform, Map<Object, Object> opts,
                      Supplier<String> contentSupplier);

    String onSection(Section section, String transform, Map<Object, Object> opts,
                     Supplier<String> contentSupplier);

    String onListing(Block block, String transform, Map<Object, Object> opts,
                     Supplier<String> contentSupplier);

    String onPreamble(Block block, String transform, Map<Object, Object> opts,
                      Supplier<String> contentSupplier);

    String onParagraph(Block block, String transform, Map<Object, Object> opts,
                       Supplier<String> contentSupplier);

    String onImage(Block block, String transform, Map<Object, Object> opts,
                   String alt, String path);

    String onAdmonition(Block block, String transform, Map<Object, Object> opts,
                        String label, Supplier<String> contentSupplier);

    String onListItem(ListItem item);

    String onList(List list, String transform, Map<Object, Object> opts);

    String onMonospaced(String value);

    String onStrong(String value);

    String onEmphasis(String value);

    String onXref(String value, String title);

    String onLink(String value);

    String onTable(Table table, String transform, Map<Object, Object> opts,
                   Function<Cell, String> cellConverter);
}
