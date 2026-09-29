package gov.openpto.ingest.service;

import gov.openpto.ingest.dto.SampleResponse;
import gov.openpto.ingest.exception.NotFoundException;
import gov.openpto.ingest.transform.DocumentFormat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Public sample files (checked in under {@code samples/}, packaged on the classpath). Only catalogued names resolve. */
@Service
public class SampleService {

    record Sample(String name, String description, DocumentFormat format) {
    }

    static final List<Sample> CATALOG = List.of(
            new Sample("ipg250107-sample.xml",
                    "Patent grant bulk file (ICE v4.7): 5 concatenated us-patent-grant documents", DocumentFormat.US_PATENT_GRANT),
            new Sample("ipa240418-sample.xml",
                    "Pre-grant application publications (ICE v4.6): 3 concatenated us-patent-application documents",
                    DocumentFormat.US_PATENT_APPLICATION),
            new Sample("pg020101-patdoc-sample.xml",
                    "Legacy 2001-2004 red book grants (PATDOC ST.32 v2.5, SGML-style DOCTYPE): 3 documents",
                    DocumentFormat.PATDOC_LEGACY),
            new Sample("apc240102-trademark-sample.xml",
                    "Trademark daily applications file: 5 case-files (registered, pending, abandoned, 66(a), cancelled)",
                    DocumentFormat.TRADEMARK_DAILY),
            new Sample("malformed-sample.xml",
                    "One valid grant followed by a truncated, not well-formed document (demonstrates PARTIAL jobs)",
                    DocumentFormat.US_PATENT_GRANT));

    public List<SampleResponse> list() {
        return CATALOG.stream()
                .map(s -> new SampleResponse(s.name(), s.description(), s.format(), size(resource(s.name()))))
                .toList();
    }

    public Resource get(String name) {
        return find(name).map(s -> resource(s.name()))
                .filter(Resource::exists)
                .orElseThrow(() -> new NotFoundException("Sample '" + name + "' not found"));
    }

    static Optional<Sample> find(String name) {
        return CATALOG.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    private static Resource resource(String name) {
        return new ClassPathResource("samples/" + name);
    }

    private static long size(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException e) {
            return -1;
        }
    }
}