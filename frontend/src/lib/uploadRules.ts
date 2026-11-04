// The backend's upload checks, repeated here so a problem shows up before the
// file is sent. They mirror DocumentService.isSupportedContentType and
// spring.servlet.multipart.max-file-size. The backend still checks every upload.

/** 20 MB, as Spring reads "20MB". */
export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024

const SUPPORTED_TYPES = ['application/pdf', 'text/plain', 'text/markdown']

/** Accept list for the file picker. */
export const ACCEPTED_FILES = '.pdf,.txt,.md,application/pdf,text/plain,text/markdown'

/** Why this file can't be uploaded, or null if it can. */
export function uploadProblem(file: File): string | null {
  // A .md file is accepted whatever type the browser reports for it.
  const supported = SUPPORTED_TYPES.includes(file.type.toLowerCase())
    || file.name.toLowerCase().endsWith('.md')
  if (!supported) {
    return 'Only PDF, .txt and .md files can be uploaded.'
  }
  if (file.size === 0) {
    return 'This file is empty.'
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return 'This file is larger than 20 MB.'
  }
  return null
}
