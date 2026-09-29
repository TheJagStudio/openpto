package gov.openpto.ingest.service;

import gov.openpto.ingest.exception.InvalidUploadException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Extracts the {@code .xml} entries of an uploaded zip into temp files, defending against:
 * <ul>
 *   <li><b>zip slip</b> — entry names with {@code ..}, absolute paths or drive letters are rejected, and entry
 *       names are never used as file-system paths (temp files are numbered);</li>
 *   <li><b>zip bombs</b> — max entries, max bytes per entry, max total bytes and max compression ratio, all
 *       enforced on the bytes actually inflated (declared sizes can lie).</li>
 * </ul>
 */
public class ZipExtractor {

    /** One extracted XML entry. */
    public record Entry(String name, Path file, long size) {
    }

    private final int maxEntries;
    private final long maxEntryBytes;
    private final long maxTotalBytes;
    private final int maxRatio;

    public ZipExtractor(int maxEntries, long maxEntryBytes, long maxTotalBytes, int maxRatio) {
        this.maxEntries = maxEntries;
        this.maxEntryBytes = maxEntryBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxRatio = maxRatio;
    }

    public List<Entry> extract(Path zip, Path targetDir, String uploadName) throws IOException {
        List<Entry> result = new ArrayList<>();
        long total = 0;
        int seen = 0;
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++seen > maxEntries) {
                    throw invalid(uploadName, "archive has more than " + maxEntries + " entries");
                }
                String name = entry.getName();
                checkEntryName(uploadName, name);
                if (entry.isDirectory() || !name.toLowerCase(Locale.ROOT).endsWith(".xml")
                        || name.startsWith("__MACOSX/") || baseName(name).startsWith(".")) {
                    continue;
                }
                Path out = targetDir.resolve("entry-" + result.size() + ".xml");
                long written = copyBounded(zipFile, entry, out, uploadName, maxTotalBytes - total);
                total += written;
                if (written == 0) {
                    throw invalid(uploadName, "entry '" + name + "' is empty");
                }
                try (InputStream head = Files.newInputStream(out)) {
                    if (ContentSniffer.sniff(head) != ContentSniffer.Kind.XML) {
                        throw invalid(uploadName, "entry '" + name + "' is not XML");
                    }
                }
                result.add(new Entry(name, out, written));
            }
        } catch (ZipException e) {
            throw invalid(uploadName, "not a readable zip archive (" + e.getMessage() + ")");
        }
        if (result.isEmpty()) {
            throw invalid(uploadName, "archive contains no .xml files");
        }
        return result;
    }

    private long copyBounded(ZipFile zipFile, ZipEntry entry, Path out, String uploadName, long totalRemaining)
            throws IOException {
        long compressed = Math.max(entry.getCompressedSize(), 1);
        long written = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = zipFile.getInputStream(entry); OutputStream os = Files.newOutputStream(out)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                written += n;
                if (written > maxEntryBytes) {
                    throw invalid(uploadName, "entry '" + entry.getName() + "' exceeds " + maxEntryBytes + " bytes");
                }
                if (written > totalRemaining) {
                    throw invalid(uploadName, "archive expands beyond " + maxTotalBytes + " bytes");
                }
                if (written > 1024 * 1024 && written / compressed > maxRatio) {
                    throw invalid(uploadName, "entry '" + entry.getName() + "' compression ratio exceeds "
                            + maxRatio + ":1 (zip bomb?)");
                }
                os.write(buffer, 0, n);
            }
        }
        return written;
    }

    static void checkEntryName(String uploadName, String name) {
        String normalized = name.replace('\\', '/');
        boolean unsafe = normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*") || normalized.contains("\0");
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) {
                unsafe = true;
            }
        }
        if (unsafe) {
            throw invalid(uploadName, "unsafe entry path '" + name + "' (zip slip)");
        }
    }

    static String baseName(String name) {
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static InvalidUploadException invalid(String uploadName, String message) {
        return new InvalidUploadException("files", uploadName + ": " + message);
    }
}