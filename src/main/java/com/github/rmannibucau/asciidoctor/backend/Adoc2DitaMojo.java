package com.github.rmannibucau.asciidoctor.backend;

import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.OptionsBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

@Mojo(defaultPhase = LifecyclePhase.GENERATE_RESOURCES, name = "adoc2dita")
public class Adoc2DitaMojo extends AbstractMojo {

    @Parameter(property = "adoc2dita.source")
    private File source;

    @Parameter(property = "adoc2dita.target")
    private File target;

    @Parameter(property = "adoc2dita.preambleAsParagraph", defaultValue = "true")
    private String preambleAsParagraph;

    @Parameter(property = "adoc2dita.excludes")
    private Collection<String> excludes;

    @Parameter(property = "adoc2dita.format", defaultValue = "true")
    private boolean format;

    @Parameter(property = "adoc2dita.formats", defaultValue = "zip")
    private Collection<String> formats;

    @Parameter(property = "adoc2dita.classifier")
    private String classifier;

    @Parameter(property = "adoc2dita.attach", defaultValue = "true")
    private boolean attach;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDirectory;

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter
    private Map<String, String> attributes;

    @Override
    public void execute() throws MojoExecutionException {
        if (!source.exists()) {
            throw new MojoExecutionException(source + " doesnt exist");
        }

        final Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        final AttributesBuilder attributes = AttributesBuilder.attributes()
                                                                 .attribute("preambleAsParagraph",
                                                                         this.preambleAsParagraph);
        ofNullable(this.attributes).ifPresent(attrs -> attrs.forEach(attributes::attribute));
        final OptionsBuilder options = OptionsBuilder.options().toFile(false).backend("dita")
                                                     .attributes(attributes);

        final TransformerFactory transformerFactory;
        final SAXParserFactory parserFactory;
        if (format) {
            transformerFactory = TransformerFactory.newInstance();
            parserFactory = SAXParserFactory.newInstance();
            parserFactory.setValidating(false);
        } else {
            transformerFactory = null;
            parserFactory = null;
        }

        final boolean fromDirectory = source.isDirectory();
        (fromDirectory ?
                Stream.of(source.listFiles(
                        (dir, name) -> !name.startsWith(".") && name.endsWith(".adoc") && (excludes == null || !excludes.contains(name)))) :
                Stream.of(source))
        .forEach(from -> {
            String output = asciidoctor.renderFile(from, options);
            if (format) {
                try {
                    final Transformer transformer = transformerFactory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                    final StreamResult result = new StreamResult(new StringWriter());
                    final SAXParser parser = parserFactory.newSAXParser();
                    final XMLReader xmlReader = parser.getXMLReader();
                    xmlReader.setEntityResolver((publicId, systemId) -> {
                        if (systemId.endsWith(".dtd")) {
                            return new InputSource(new StringReader(" "));
                        }
                        return null;
                    });
                    transformer.transform(new SAXSource(xmlReader, new InputSource(new StringReader(output))), result);
                    output = result.getWriter().toString();
                } catch (final ParserConfigurationException | SAXException | TransformerException e) {
                    getLog().warn(e.getMessage(), e);
                }
            }
            final File outputFile = fromDirectory ?
                    new File(target, from.getName().replace(".adoc", ".dita")) : target;
            outputFile.getParentFile().mkdirs();
            try (final Writer w = new BufferedWriter(new FileWriter(outputFile))) {
                w.write(output);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            getLog().info("Created " + outputFile);
        });

        if (fromDirectory) {
            final Path prefix = target.toPath().toAbsolutePath();
            formats.forEach(format -> {
                getLog().info(format + "-ing dita sources");

                final File output = new File(buildDirectory, artifactId + "-dita-bundle." + format);

                switch (format.toLowerCase(ROOT)) {
                    case "tar.gz":
                        try (final TarArchiveOutputStream tarGz = new TarArchiveOutputStream(new GZIPOutputStream(new
                                FileOutputStream(output)))) {
                            tarGz.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                            for (final String entry : target.list()) {
                                tarGz(tarGz, new File(target, entry), prefix);
                            }
                        } catch (final IOException e) {
                            throw new IllegalStateException(e.getMessage(), e);
                        }
                        break;
                    case "zip":
                        try (final ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new FileOutputStream
                                (output))) {
                            for (final String entry : target.list()) {
                                zip(zos, new File(target, entry), prefix);
                            }
                        } catch (final IOException e) {
                            throw new IllegalStateException(e.getMessage(), e);
                        }
                        break;
                    default:
                        throw new IllegalArgumentException(format + " is not supported");
                }

                attach(format, output);
            });
        } else if (formats != null && !formats.isEmpty()) {
            getLog().warn("You can't bundle a single file, move source/target to directories");
        }
    }

    private void tarGz(final TarArchiveOutputStream tarGz, final File f, final Path prefix) throws IOException {
        final String path = prefix.relativize(f.toPath()).toString().replace(File.separator, "/");
        final TarArchiveEntry archiveEntry = new TarArchiveEntry(f, path);
        tarGz.putArchiveEntry(archiveEntry);
        if (f.isDirectory()) {
            tarGz.closeArchiveEntry();
            final File[] files = f.listFiles();
            if (files != null) {
                for (final File child : files) {
                    tarGz(tarGz, child, prefix);
                }
            }
        } else {
            Files.copy(f.toPath(), tarGz);
            tarGz.closeArchiveEntry();
        }
    }

    private void zip(final ZipArchiveOutputStream zip, final File f, final Path prefix) throws IOException {
        final String path = prefix.relativize(f.toPath()).toString().replace(File.separator, "/");
        final ZipArchiveEntry archiveEntry = new ZipArchiveEntry(f, path);
        zip.putArchiveEntry(archiveEntry);
        if (f.isDirectory()) {
            zip.closeArchiveEntry();
            final File[] files = f.listFiles();
            if (files != null) {
                for (final File child : files) {
                    zip(zip, child, prefix);
                }
            }
        } else {
            Files.copy(f.toPath(), zip);
            zip.closeArchiveEntry();
        }
    }

    private void attach(final String ext, final File output) {
        if (attach) {
            getLog().info("Attaching Custom Meecrowave Distribution (" + ext + ")");
            if (classifier != null) {
                projectHelper.attachArtifact(project, ext, classifier, output);
            } else {
                projectHelper.attachArtifact(project, ext, output);
            }
        }
    }
}
