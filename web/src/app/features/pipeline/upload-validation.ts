import { formatBytes } from '../../core/format';
import { INGEST_LIMITS } from '../../core/models';

export interface RejectedFile {
  name: string;
  reason: string;
}

export interface FileCheck {
  accepted: File[];
  rejected: RejectedFile[];
}

/** Client-side mirror of the ingest service limits: 1–10 files, `.xml`/`.zip`, ≤ 50 MB each. */
export function checkFiles(files: readonly File[], alreadySelected = 0): FileCheck {
  const accepted: File[] = [];
  const rejected: RejectedFile[] = [];
  for (const file of files) {
    const lower = file.name.toLowerCase();
    if (!INGEST_LIMITS.extensions.some((ext) => lower.endsWith(ext))) {
      rejected.push({ name: file.name, reason: 'Only .xml and .zip files are accepted.' });
    } else if (file.size > INGEST_LIMITS.maxBytes) {
      rejected.push({ name: file.name, reason: `${formatBytes(file.size)} exceeds the ${formatBytes(INGEST_LIMITS.maxBytes)} limit.` });
    } else if (file.size === 0) {
      rejected.push({ name: file.name, reason: 'File is empty.' });
    } else if (alreadySelected + accepted.length >= INGEST_LIMITS.maxFiles) {
      rejected.push({ name: file.name, reason: `At most ${INGEST_LIMITS.maxFiles} files per upload.` });
    } else {
      accepted.push(file);
    }
  }
  return { accepted, rejected };
}
