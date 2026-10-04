/**
 * Client-side mirror of the server's FileTypePolicy (esg-backend files/FileTypePolicy.java): the extension decides the
 * type and the leading bytes must match it. The server re-checks everything; this only gives earlier feedback.
 */
const PDF = [0x25, 0x50, 0x44, 0x46];
const PNG = [0x89, 0x50, 0x4e, 0x47];
const JPG = [0xff, 0xd8, 0xff];
const ZIP = [0x50, 0x4b, 0x03, 0x04];
const OLE = [0xd0, 0xcf, 0x11, 0xe0];

export const FILE_TYPES = {
  pdf: { signatures: [PDF] },
  png: { signatures: [PNG] },
  jpg: { signatures: [JPG] },
  jpeg: { signatures: [JPG] },
  xlsx: { signatures: [ZIP] },
  docx: { signatures: [ZIP] },
  xls: { signatures: [OLE] },
  doc: { signatures: [OLE] },
  csv: { text: true },
  txt: { text: true },
};

export const extensionOf = (name) => {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
};

const startsWith = (head, sig) => sig.every((b, i) => head[i] === b);

export const formatBytes = (n) => {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
};

/** Returns an error message, or null when the file passes the same checks the server applies. */
export async function checkFile(file, maxBytes) {
  const ext = extensionOf(file.name);
  const type = FILE_TYPES[ext];
  if (!type) return `Allowed file types: ${Object.keys(FILE_TYPES).sort().join(', ')}`;
  if (file.size === 0) return 'The file is empty';
  if (maxBytes && file.size > maxBytes) return `The file is larger than the ${formatBytes(maxBytes)} limit`;
  const head = new Uint8Array(await file.slice(0, 8192).arrayBuffer());
  const ok = type.text ? !head.includes(0) : type.signatures.some((sig) => startsWith(head, sig));
  return ok ? null : `The file's content doesn't match its .${ext} extension`;
}
