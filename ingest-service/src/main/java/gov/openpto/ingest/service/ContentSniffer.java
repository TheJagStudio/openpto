package gov.openpto.ingest.service;

import java.io.IOException;
import java.io.InputStream;

/** Detects the real content type from the first bytes instead of trusting the file extension. */
public final class ContentSniffer {

    public enum Kind {XML, ZIP, OTHER}

    private static final int PEEK = 512;

    private ContentSniffer() {
    }

    public static Kind sniff(InputStream in) throws IOException {
        byte[] head = in.readNBytes(PEEK);
        return sniff(head, head.length);
    }

    public static Kind sniff(byte[] head, int length) {
        if (length >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
            return Kind.ZIP;
        }
        int i = 0;
        // UTF-8 BOM
        if (length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < length && (head[i] == ' ' || head[i] == '\t' || head[i] == '\r' || head[i] == '\n')) {
            i++;
        }
        if (i + 1 < length && head[i] == '<') {
            byte next = head[i + 1];
            if (next == '?' || next == '!' || Character.isLetter(next) || next == '_') {
                return Kind.XML;
            }
        }
        return Kind.OTHER;
    }
}