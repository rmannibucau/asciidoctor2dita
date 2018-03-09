package com.github.rmannibucau.asciidoctor.backend.dita;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.text.StringEscapeUtils.unescapeHtml4;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.text.translate.AggregateTranslator;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.apache.commons.text.translate.EntityArrays;
import org.apache.commons.text.translate.LookupTranslator;
import org.apache.commons.text.translate.NumericEntityUnescaper;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.List;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.Table;

import com.github.rmannibucau.asciidoctor.backend.Aggregator;
import com.github.rmannibucau.asciidoctor.backend.DocumentVisitor;

import lombok.RequiredArgsConstructor;

public class DitaVisitor implements DocumentVisitor {

    public static final CharSequenceTranslator UNESCAPE = new AggregateTranslator(
            // new LookupTranslator(EntityArrays.BASIC_UNESCAPE),
            new LookupTranslator(EntityArrays.ISO8859_1_UNESCAPE), new LookupTranslator(EntityArrays.HTML40_EXTENDED_UNESCAPE),
            new NumericEntityUnescaper());

    private final Collection<String> ids = new HashSet<>();

    private Aggregator aggregator;

    private VisitedSection rootSection;

    private VisitedSection currentSection;

    private boolean inTable = false;

    @Override
    public void setAggregator(final Aggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public String onDocument(final Document document, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        if ("table".equals(transform) || "unwrap".equals(transform) || inTable) {
            return contentSupplier.get();
        }

        rootSection = null;
        ids.clear();
        ids.add("generated-");

        final String filename = ofNullable(opts.remove("originalFile")).map(Object::toString).orElse(null);
        final String title = document.getDoctitle();
        final String id = extractId(document, title);
        final String content = contentSupplier.get();

        if (currentSection == rootSection && rootSection != null && !rootSection.children.isEmpty()) {
            final String name = "dm-" + sanitizeId(id);
            final String map = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n" + "<map id=\"" + name
                    + "\" xml:lang=\"en\">\n" + " <title>" + title + "</title>\n"
                    // todo: manage <mapref href="xxx.ditamap"/>?
                    + rootSection.children.stream().map(VisitedSection::toDita).collect(joining("\n")) + "</map>";

            if (aggregator != null) {
                final String baseName = ofNullable(filename).map(f -> f.replaceFirst(".adoc", ""))
                        .orElseGet(() -> sanitizeId(id));
                aggregator.getDocuments().put("dm-" + baseName + ".ditamap", map);
            }

            return map;
        }
        return toConcept(title, content, "c-" + sanitizeId(ofNullable(id).orElse("generated-")));
    }

    @Override
    public String onSection(final Section section, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        final String title = section.getTitle();
        final String id = extractId(section, null);

        final String name = "c-" + sanitizeId(ofNullable(id).orElseGet(() -> extractId(section, title)));
        if (rootSection == null) {
            rootSection = new VisitedSection(name, null);
            currentSection = rootSection;
        } else {
            final VisitedSection self = new VisitedSection(name, currentSection);
            currentSection.children.add(self);
            currentSection = self;
        }

        final Function<Boolean, String> content = inSection -> {
            // note: this should be dropped but not sure yet the best way to do it in dita
            final String tag = inSection ? "sectiondiv" : "section";
            return "<" + tag + ofNullable(id).map(i -> " id=\"" + id + "\"").orElse("") + ">"
                    + ofNullable(title).map(t -> inSection ? ("<b>" + t + "</b>") : ("<title>" + t + "</title>\n")).orElse("")
                    + contentSupplier.get() + "</" + tag + ">\n";
        };
        try {
            return content.apply(currentSection != rootSection);
        } finally {
            currentSection = ofNullable(currentSection.parent).orElse(rootSection);

            if (aggregator != null) {
                final String concept = toConcept(title, content.apply(false), name);
                aggregator.getDocuments().put(name + ".dita", concept);
            }
        }
    }

    @Override
    public String onListing(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        return "<codeblock>" + unescapeHtml4(contentSupplier.get()) + "</codeblock>\n";
    }

    @Override
    public String onPreamble(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        return "<abstract>" + contentSupplier.get() + "</abstract>\n";
    }

    @Override
    public String onParagraph(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        final String content = contentSupplier.get();
        if (content.startsWith("<codeph>")) { // already escaped
            return content;
        }
        if (block.getBlocks().isEmpty()) {
            if (inTable) {
                return content;
            }
            return "<p>" + content + "</p>\n";
        }
        return content;
    }

    @Override
    public String onImage(final ContentNode block, final String transform, final Map<Object, Object> opts, final String alt,
            final String path) {
        final String id = extractId(block, path.replace("/", "_").replace(".", "_"));
        aggregator.getResources().add(new File(aggregator.getImages(), path));
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
            return "<simpletable frame=\"all\">"
                    + (table.getHeader().isEmpty() ? ""
                            : convertTableRows(table.getHeader(), cellConverter, "sthead").collect(joining("\n")))
                    + "\n" + convertTableRows(table.getBody(), cellConverter, "strow").collect(joining("\n")) + "\n"
                    + (table.getFooter().isEmpty() ? ""
                            : convertTableRows(table.getFooter(), cellConverter, "strow").collect(joining("\n")))
                    + "</simpletable>\n";
        } finally {
            if (!wasInTable) {
                inTable = false;
            }
        }
    }

    @Override
    public String onPassthrough(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        return contentSupplier.get();
    }

    @Override
    public String onQuote(final Block block, final String transform, final Map<Object, Object> opts,
            final Supplier<String> contentSupplier) {
        return "<lq>" + contentSupplier.get() + "</lq>";
    }

    @Override
    public String onList(final List list, final String transform, final Map<Object, Object> opts) {
        return list.getItems().stream().map(ListItem.class::cast).map(this::onListItem)
                .collect(joining("\n", "<ul>\n", "</ul>\n"));
    }

    @Override
    public String onDescriptionList(final DescriptionList list, final String transform, final Map<Object, Object> opts) {
        return list.getItems().stream().map(DescriptionListEntry.class::cast).map(this::onDescriptionListItem)
                .collect(joining("\n", "<ul>\n", "</ul>\n"));
    }

    @Override
    public String onMonospaced(final String value) {
        return "<codeph>" + value + "</codeph>";
    }

    @Override
    public String onXref(final String value, final String ref) {
        String link = ref;
        int anchor = link.indexOf('#');
        String anchorValue = "";
        if (anchor > 0) {
            anchorValue = '#' + link.substring(anchor + 1);
            link = link.substring(0, anchor).replace(".adoc", "");
        }
        final boolean isMap = aggregator.fileExists("dm-" + link + ".ditamap");
        link = (isMap ? "dm" : "c") + "-" +  link +".dita" + (isMap ? "map" : "") + anchorValue;
        return "<xref href=\"" + link + "\">" + value + "</xref>";
    }

    @Override
    public String onLink(final String value) {
        return "<xref href=\"" + value + "\" format=\"html\" scope=\"external\">" + value + "</xref>";
    }

    @Override
    public String onLine(final String value) {
        return value;
    }

    @Override
    public String onStrong(final String value) {
        return "<b>" + value + "</b>";
    }

    @Override
    public String onEmphasis(final String value) {
        return "<i>" + value + "</i>";
    }

    @Override
    public String onCallout(final String value) {
        return ""; // we use list anyway
    }

    @Override
    public String transformRawContent(final String value, final boolean complete) {
        return (complete ? "<![CDATA[" : "") + UNESCAPE.translate(value) + (complete ? "]]>" : "");
    }

    private String sanitizeId(final String id) {
        return id.replaceFirst("^_*", "").replaceFirst("/", "_");
    }

    private String toConcept(final String title, final String content, final String name) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD" + " DITA "
                + "Concept//EN\" \"concept.dtd\">\n" + "<concept id=\"" + name + "\" xml:lang=\"en\">" + "<title>" + title
                + "</title>" + "<conbody>" + sanitize(content) + "</conbody>" + "</concept>";
    }

    private String onDescriptionListItem(final DescriptionListEntry item) {
        return "<li>" + item.getDescription().getText() + ": "
                + item.getTerms().stream().map(ListItem::getText).collect(joining(". ")) + "</li>";
    }

    private String onListItem(final ListItem item) {
        return "<li>" + item.getText() + "</li>";
    }

    private Stream<String> convertTableRows(final java.util.List<Row> rows, final Function<Cell, String> cellConverter,
            final String rowMarker) {
        return rows.stream().map(row -> onRow(row, cellConverter, rowMarker)).filter(l -> !l.isEmpty());
    }

    private String onRow(final Row row, final Function<Cell, String> cellConverter, final String rowMarker) {
        return "<" + rowMarker + ">" + row.getCells().stream().map(cellConverter)
                .collect(joining("</stentry>\n<stentry>", "<stentry>", "</stentry>\n")) + "</" + rowMarker + ">";
    }

    private String extractId(final ContentNode document, final String title) {
        String idBase = ofNullable(document.getId())
                .orElseGet(() -> ofNullable(title).map(t -> t.replace(" ", "_").replaceFirst("::", "__")).orElse(null));
        if (idBase == null) {
            return null;
        }
        int i = 1;
        while (!ids.add(idBase)) {
            idBase += i++;
        }
        return idBase;
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

    @RequiredArgsConstructor
    private static class VisitedSection {

        private final String id;

        private final VisitedSection parent;

        private final Collection<VisitedSection> children = new ArrayList<>();

        String toDita() {
            final boolean hasChild = !children.isEmpty();
            return "<topicref href=\"" + id + ".dita\""
                    + (hasChild ? ">\n" + children.stream().map(VisitedSection::toDita).collect(joining("\n")) + "</topicref>\n"
                            : "/>\n");
        }
    }
}
