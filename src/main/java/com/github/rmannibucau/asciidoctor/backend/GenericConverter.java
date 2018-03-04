package com.github.rmannibucau.asciidoctor.backend;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Locale.ROOT;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import com.github.rmannibucau.asciidoctor.backend.dita.DitaVisitor;

import org.apache.commons.text.StringEscapeUtils;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.List;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.asciidoctor.converter.ConverterFor;
import org.asciidoctor.converter.StringConverter;
import org.asciidoctor.converter.spi.ConverterRegistry;

@ConverterFor("dita")
public class GenericConverter extends StringConverter implements ConverterRegistry {
    private final boolean preambleAsParagraph;
    private final DocumentVisitor visitor;

    public GenericConverter() { // for the SPI
        this("dita", emptyMap());
    }

    public GenericConverter(final String backend, final Map<String, Object> opts) {
        super(backend, opts);
        this.preambleAsParagraph = "true".equalsIgnoreCase(opts.getOrDefault("preambleAsParagraph", "true").toString());
        this.visitor = createVisitor(opts.get("visitor"));
    }

    @Override
    public String convert(final ContentNode node, final String transform, final Map<Object, Object> opts) {
        if (Document.class.isInstance(node)) {
            final Document document = Document.class.cast(node);
            return visitor.onDocument(document, transform, opts, convertChildren(document));
        } else if (Section.class.isInstance(node)) {
            final Section section = Section.class.cast(node);
            return visitor.onSection(section, transform, opts, convertChildren(section));
        } else if (Block.class.isInstance(node)) {
            final Block block = Block.class.cast(node);
            final String context = block.getContext();
            final Map<String, Object> attributes = block.getAttributes();
            final String content = block.getLines().stream().collect(joining("\n"));

            switch (ofNullable(context).orElse("").toLowerCase(ROOT)) {
                case "listing":
                    return visitor.onListing(block, transform, opts, block.getLines().stream().collect(joining("\n")));
                case "paragraph":
                    return visitor.onParagraph(block, transform, opts, block.getLines().stream().collect(joining("\n")));
                case "preamble":
                    final String children = convertChildren(block);
                    if (preambleAsParagraph) {
                        return visitor.onParagraph(block, transform, opts, children);
                    }
                    return visitor.onPreamble(block, transform, opts, children);
                case "image":
                    final String path = attributes.get("target").toString();
                    return visitor.onImage(block, transform, opts, attributes.getOrDefault("alt", path).toString(), path);
                case "admonition":
                    final String label = String.valueOf(attributes.getOrDefault("textlabel", "Note"));
                    return visitor.onAdmonition(block, transform, opts, label, content);
                default:
                    throw new IllegalArgumentException("Unsupported block type: " + context);
            }
        } else if (List.class.isInstance(node)) {
            return visitor.onList(List.class.cast(node), transform, opts);
        } else if (PhraseNode.class.isInstance(node)) {
            final PhraseNode phraseNode = PhraseNode.class.cast(node);
            final String context = phraseNode.getContext();
            final String type = phraseNode.getType();
            final String text = "quoted".equals(context) ? StringEscapeUtils.unescapeHtml4(phraseNode.getText()) : phraseNode.getText();

            switch (ofNullable(type).orElse("")) {
                case "monospaced":
                    return visitor.onMonospaced(text);
                case "strong":
                    return visitor.onStrong(text);
                case "emphasis":
                    return visitor.onEmphasis(text);
                default:
                    throw new IllegalArgumentException("Unsupported phrase node type: " + type);
            }
        } else if (Table.class.isInstance(node)) {
            return visitor.onTable(Table.class.cast(node), transform, opts, cell -> of(cell)
                    .filter(c -> "asciidoc".equalsIgnoreCase(c.getStyle()))
                    .map(Cell::getInnerDocument)
                    .map(d -> convert(d, "table", singletonMap("preambleAsParagraph", preambleAsParagraph)))
                    .orElseGet(cell::getText));
        }

        throw new IllegalArgumentException("Unsupported node " + node);
    }

    private String convertChildren(final StructuralNode node) {
        return ofNullable(node.getBlocks())
                .filter(b -> !b.isEmpty())
                .map(blocks -> blocks.stream().map(StructuralNode::convert).collect(joining("\n")))
                .orElseThrow(() -> new IllegalStateException("No child for " + node));
    }

    @Override
    public void register(final Asciidoctor asciidoctor) {
        asciidoctor.javaConverterRegistry().register(GenericConverter.class);
    }

    private DocumentVisitor createVisitor(final Object visitor) {
        if (visitor == null) {
            return new DitaVisitor();
        }
        if (DocumentVisitor.class.isInstance(visitor)) {
            return DocumentVisitor.class.cast(visitor);
        }
        Class<?> type = null;
        if (String.class.isInstance(visitor)) {
            try {
                type = Thread.currentThread().getContextClassLoader().loadClass(String.valueOf(visitor).trim());
            } catch (final ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (Class.class.isInstance(visitor)) {
            type = Class.class.cast(visitor);
        }
        if (type != null) {
            try {
                return DocumentVisitor.class.cast(type.getConstructor().newInstance());
            } catch (final InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                throw new IllegalStateException(e);
            } catch (final InvocationTargetException e) {
                throw new IllegalStateException(e.getTargetException());
            }
        }
        throw new IllegalArgumentException("Unsupported parameter: " + visitor);
    }
}
