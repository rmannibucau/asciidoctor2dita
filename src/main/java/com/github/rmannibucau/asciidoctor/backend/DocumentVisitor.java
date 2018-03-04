package com.github.rmannibucau.asciidoctor.backend;

import java.util.Map;
import java.util.function.Function;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.List;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.Table;

public interface DocumentVisitor {
    String onDocument(Document document, String transform, Map<Object, Object> opts,
                      String content);

    String onSection(Section section, String transform, Map<Object, Object> opts,
                     String content);

    String onListing(Block block, String transform, Map<Object, Object> opts,
                     String content);

    String onPreamble(Block block, String transform, Map<Object, Object> opts,
                      String content);

    String onParagraph(Block block, String transform, Map<Object, Object> opts,
                       String content);

    String onImage(Block block, String transform, Map<Object, Object> opts,
                   String alt, String path);

    String onAdmonition(Block block, String transform, Map<Object, Object> opts,
                        String label, String content);

    String onListItem(ListItem item);

    String onList(List list, String transform, Map<Object, Object> opts);

    String onMonospaced(String value);

    String onStrong(String value);

    String onEmphasis(String value);

    String onTable(Table table, String transform, Map<Object, Object> opts,
                   Function<Cell, String> cellConverter);
}
