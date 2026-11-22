const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const PDF_MIME = 'application/pdf';
const ANCHOR_REMOVAL_MS = 1500;

// The in-app browser may expose the File System Access API but refuse writes.
// Once that happens, stop offering the picker for the rest of this page session.
let pickerBlockedForSession = false;

export function isSavePickerBlocked() {
  return pickerBlockedForSession;
}

const SIGNATURES = {
  pdf: [0x25, 0x50, 0x44, 0x46, 0x2d], // %PDF-
  xlsx: [0x50, 0x4b], // PK (ZIP container)
};

/**
 * Parses Content-Disposition without trusting the header blindly. RFC 5987
 * filename*=UTF-8''... takes precedence over the quoted filename parameter.
 */
export function parseContentDisposition(header) {
  if (typeof header !== 'string' || header.trim() === '') return null;

  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header);
  if (encoded) {
    try {
      const value = decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ''));
      if (value) return sanitizeFilename(value);
    } catch (decodeError) {
      // Fall through to the plain filename parameter.
    }
  }

  const quoted = /filename\s*=\s*"([^"]+)"/i.exec(header);
  if (quoted) return sanitizeFilename(quoted[1]);

  const plain = /filename\s*=\s*([^;]+)/i.exec(header);
  if (plain) return sanitizeFilename(plain[1]);

  return null;
}

function sanitizeFilename(value) {
  const base = String(value)
    .split(/[\\/]/)
    .pop()
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .trim();
  if (!base || base === '.' || base === '..') return null;
  return base.slice(0, 180);
}

function signatureMatches(bytes, extension) {
  const signature = SIGNATURES[extension] || SIGNATURES.pdf;
  return signature.every((byte, index) => bytes[index] === byte);
}

export function isSavePickerSupported() {
  return typeof window !== 'undefined' && typeof window.showSaveFilePicker === 'function';
}

/**
 * Validates a binary report response and keeps the generated Blob in memory
 * until the user saves or dismisses it. No download or file picker is started
 * here — those must be triggered directly by a user click.
 */
export async function downloadBinaryResponse(response, { fallbackName = 'taskflow-report', extension = 'pdf' } = {}) {
  const data = response?.data;
  const isBlob = typeof Blob !== 'undefined' && data instanceof Blob;
  const isBuffer = typeof ArrayBuffer !== 'undefined' && data instanceof ArrayBuffer;

  if (!isBlob && !isBuffer) {
    throw new Error('The report response did not contain a file.');
  }

  const bytes = isBuffer ? new Uint8Array(data) : new Uint8Array(await data.arrayBuffer());
  if (bytes.length === 0) {
    throw new Error('The report file was empty.');
  }

  const headers = response.headers || {};
  const rawContentType = headers['content-type'] || headers['Content-Type'] || '';
  const contentType = String(rawContentType).split(';')[0].trim().toLowerCase();

  if (contentType.includes('json') || contentType.includes('text/')) {
    throw new Error('The server returned an error message instead of a report file.');
  }

  const safeExtension = extension === 'xlsx' ? 'xlsx' : 'pdf';
  if (!signatureMatches(bytes, safeExtension)) {
    throw new Error(
      safeExtension === 'xlsx'
        ? 'The Excel file was invalid or corrupted.'
        : 'The PDF file was invalid or corrupted.'
    );
  }

  const serverFilename = parseContentDisposition(
    headers['content-disposition'] || headers['Content-Disposition']
  );
  const filename = serverFilename || `${fallbackName}-${Date.now()}.${safeExtension}`;
  const mimeType = contentType || (safeExtension === 'xlsx' ? XLSX_MIME : PDF_MIME);
  const blob = isBlob ? data : new Blob([data], { type: mimeType });

  return {
    filename,
    extension: safeExtension,
    mimeType,
    blob,
    cleanup: () => {},
  };
}

function isCancellation(error) {
  return error?.name === 'AbortError' || error?.code === 20;
}

function isPermissionFailure(error) {
  return ['NotAllowedError', 'SecurityError', 'NotSupportedError', 'InvalidStateError']
    .includes(error?.name);
}

/** Standard download fallback: the browser, not TaskFlow, chooses the folder. */
function startStandardDownload(report, reason) {
  if (report.blob.size === 0) {
    throw new Error('The report file was empty.');
  }

  const objectUrl = URL.createObjectURL(report.blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = report.filename;
  anchor.style.display = 'none';
  document.body.appendChild(anchor);

  let anchorRemoved = false;
  let urlRevoked = false;
  const removeAnchor = () => {
    if (anchorRemoved) return;
    anchorRemoved = true;
    if (anchor.parentNode) anchor.parentNode.removeChild(anchor);
  };
  // The URL is revoked only by the caller (next save / unmount), never on a
  // short timer: some browsers need longer than a second to flush a download
  // and revoking early produces a 0-byte file.
  const cleanup = () => {
    removeAnchor();
    if (urlRevoked) return;
    urlRevoked = true;
    URL.revokeObjectURL(objectUrl);
  };

  try {
    anchor.click();
  } catch (downloadError) {
    cleanup();
    throw new Error('The browser blocked the report download.');
  }
  setTimeout(removeAnchor, ANCHOR_REMOVAL_MS);

  return {
    initiated: true,
    fallback: reason === 'blocked',
    reason,
    filename: report.filename,
    bytes: report.blob.size,
    url: objectUrl,
    cleanup,
  };
}

/**
 * Saves a validated report Blob. Call this directly from a user click so the
 * File System Access API retains its user-activation requirement.
 *
 * Outcomes:
 * - { cancelled: true } — user dismissed the picker; report stays available
 * - { saved: true, filename } — native write and close both succeeded
 * - { initiated: true, fallback, reason, filename } — standard download used
 */
export async function saveReportFile(report) {
  if (!report?.blob || typeof report.blob.arrayBuffer !== 'function') {
    throw new Error('No report file is available to save.');
  }
  if (report.blob.size === 0) {
    throw new Error('The report file was empty.');
  }
  if (!report.filename) {
    throw new Error('The report filename is missing.');
  }

  if (pickerBlockedForSession) {
    return startStandardDownload(report, 'blocked');
  }

  if (!isSavePickerSupported()) {
    return startStandardDownload(report, 'unsupported');
  }

  // Must be the first await — no preceding async work may consume activation.
  let handle;
  try {
    handle = await window.showSaveFilePicker({
      suggestedName: report.filename,
      types: [
        {
          description: report.extension === 'xlsx' ? 'Excel workbook' : 'PDF document',
          accept: { [report.mimeType]: [`.${report.extension}`] },
        },
      ],
    });
  } catch (pickerError) {
    if (isCancellation(pickerError)) return { cancelled: true };
    if (isPermissionFailure(pickerError)) {
      pickerBlockedForSession = true;
      return startStandardDownload(report, 'blocked');
    }
    throw new Error('The file picker could not be opened.');
  }

  let writable;
  try {
    writable = await handle.createWritable();
  } catch (writeSetupError) {
    if (isPermissionFailure(writeSetupError)) {
      pickerBlockedForSession = true;
      return startStandardDownload(report, 'blocked');
    }
    throw new Error('The selected file could not be opened for writing.');
  }

  try {
    await writable.write(report.blob);
  } catch (writeError) {
    await writable.abort().catch(() => {});
    throw new Error('The report could not be written to the selected file.');
  }

  try {
    await writable.close();
  } catch (closeError) {
    await writable.abort().catch(() => {});
    throw new Error('The report could not be finalized in the selected file.');
  }

  return { saved: true, filename: handle.name || report.filename };
}
