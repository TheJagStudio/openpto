package gov.openpto.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.openpto.ingest.exception.InvalidUploadException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipExtractorTest {

    @TempDir
    Path dir;

    private Path zip(String name, int entries, long bytesPerEntry, byte fill) throws IOException {
        Path zip = dir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int e = 0; e < entries; e++) {
                out.putNextEntry(new ZipEntry("doc" + e + ".xml"));
                out.write("<?xml version=\"1.0\"?><a>".getBytes());
                byte[] chunk = new byte[64 * 1024];
                java.util.Arrays.fill(chunk, fill);
                for (long written = 0; written < bytesPerEntry; written += chunk.length) {
                    out.write(chunk, 0, (int) Math.min(chunk.length, bytesPerEntry - written));
                }
                out.write("</a>".getBytes());
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void extract_zipBomb_highCompressionRatioRejected() throws IOException {
        Path bomb = zip("bomb.zip", 1, 20L * 1024 * 1024, (byte) ' ');
        Path out = Files.createDirectory(dir.resolve("out"));

        assertThatThrownBy(() -> new ZipExtractor(10, 50L << 20, 100L << 20, 100).extract(bomb, out, "bomb.zip"))
                .isInstanceOf(InvalidUploadException.class).hasMessageContaining("compression ratio");
    }

    @Test
    void extract_entryAndTotalAndCountLimits() throws IOException {
        Path three = zip("three.zip", 3, 100_000, (byte) 'x');
        Path out = Files.createDirectory(dir.resolve("out"));

        assertThatThrownBy(() -> new ZipExtractor(2, 1 << 20, 10 << 20, 1000).extract(three, out, "three.zip"))
                .hasMessageContaining("more than 2 entries");
        assertThatThrownBy(() -> new ZipExtractor(10, 50_000, 10 << 20, 1000).extract(three, out, "three.zip"))
                .hasMessageContaining("exceeds 50000 bytes");
        assertThatThrownBy(() -> new ZipExtractor(10, 1 << 20, 250_000, 1000).extract(three, out, "three.zip"))
                .hasMessageContaining("expands beyond");
    }

    @Test
    void extract_validArchive_returnsNumberedTempFiles() throws IOException {
        Path ok = zip("ok.zip", 2, 10, (byte) 'x');
        Path out = Files.createDirectory(dir.resolve("out"));

        List<ZipExtractor.Entry> entries = new ZipExtractor(10, 1 << 20, 10 << 20, 100).extract(ok, out, "ok.zip");

        assertThat(entries).extracting(ZipExtractor.Entry::name).containsExactly("doc0.xml", "doc1.xml");
        assertThat(entries).extracting(e -> e.file().getFileName().toString()).containsExactly("entry-0.xml", "entry-1.xml");
    }

    @Test
    void extract_corruptArchive_isRejected() throws IOException {
        Path corrupt = dir.resolve("corrupt.zip");
        try (OutputStream out = Files.newOutputStream(corrupt)) {
            out.write(new byte[]{'P', 'K', 3, 4, 1, 2, 3});
        }

        assertThatThrownBy(() -> new ZipExtractor(10, 1 << 20, 10 << 20, 100).extract(corrupt, dir, "corrupt.zip"))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void checkEntryName_rejectsTraversalAbsoluteAndDrivePaths() {
        for (String bad : List.of("../x.xml", "a/../../x.xml", "/etc/x.xml", "C:\\x.xml", "a\\..\\..\\x.xml")) {
            assertThatThrownBy(() -> ZipExtractor.checkEntryName("u.zip", bad)).hasMessageContaining("zip slip");
        }
        ZipExtractor.checkEntryName("u.zip", "folder/ok..name.xml");
    }

    @Test
    void contentSniffer_detectsXmlZipAndOther() {
        assertThat(ContentSniffer.sniff("\uFEFF  <?xml?>".getBytes(java.nio.charset.StandardCharsets.UTF_8), 12))
                .isEqualTo(ContentSniffer.Kind.XML);
        assertThat(ContentSniffer.sniff("<!DOCTYPE x>".getBytes(), 12)).isEqualTo(ContentSniffer.Kind.XML);
        assertThat(ContentSniffer.sniff("\n<root/>".getBytes(), 8)).isEqualTo(ContentSniffer.Kind.XML);
        assertThat(ContentSniffer.sniff(new byte[]{'P', 'K', 3, 4}, 4)).isEqualTo(ContentSniffer.Kind.ZIP);
        assertThat(ContentSniffer.sniff("< not xml".getBytes(), 9)).isEqualTo(ContentSniffer.Kind.OTHER);
        assertThat(ContentSniffer.sniff("{}".getBytes(), 2)).isEqualTo(ContentSniffer.Kind.OTHER);
        assertThat(ContentSniffer.sniff(new byte[0], 0)).isEqualTo(ContentSniffer.Kind.OTHER);
    }
}