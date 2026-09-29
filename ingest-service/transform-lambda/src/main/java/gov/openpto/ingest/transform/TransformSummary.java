package gov.openpto.ingest.transform;

import java.util.List;

/**
 * Outcome of transforming one file.
 *
 * @param format         format of the first recognised document ({@code UNKNOWN} if none)
 * @param documents      XML documents found in the file
 * @param recordsTotal   records attempted (ok + failed)
 * @param recordsOk      records written to the sink
 * @param recordsFailed  records that failed (malformed XML, unsupported type, missing fields)
 * @param errors         first {@code maxErrors} errors
 */
public record TransformSummary(DocumentFormat format, int documents, int recordsTotal, int recordsOk,
                               int recordsFailed, List<RecordError> errors) {
}