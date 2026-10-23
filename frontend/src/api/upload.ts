import { ref } from 'vue'
import { ApiError, http } from './http'
import type { FileInfo, FilePurpose, UploadTicket } from './types'

/** Content types the browser may leave empty, by extension. */
const TYPES_BY_EXTENSION: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
}

/** Uploads in flight, so forms can wait for them before submitting. */
export const activeUploads = ref(0)

export function contentTypeOf(file: File): string {
  if (file.type) {
    return file.type
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return TYPES_BY_EXTENSION[extension] ?? 'application/octet-stream'
}

/**
 * Uploads a file the way the API expects: ask for a pre-signed URL, send the bytes straight to storage, then
 * confirm so the service can check what arrived. The bytes never pass through the services.
 */
export async function uploadFile(
  file: File,
  purpose: FilePurpose,
  onProgress?: (fraction: number) => void,
): Promise<FileInfo> {
  activeUploads.value++
  try {
    const ticket = await http.post<UploadTicket>('/files', {
      fileName: file.name,
      contentType: contentTypeOf(file),
      size: file.size,
      purpose,
    })
    await put(ticket, file, onProgress)
    return await http.post<FileInfo>(`/files/${ticket.fileId}/complete`)
  } finally {
    activeUploads.value--
  }
}

/** XMLHttpRequest rather than fetch, because only it reports upload progress. */
function put(ticket: UploadTicket, file: File, onProgress?: (fraction: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open(ticket.method || 'PUT', ticket.uploadUrl)
    for (const [name, value] of Object.entries(ticket.headers ?? {})) {
      request.setRequestHeader(name, value)
    }
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress?.(event.loaded / event.total)
      }
    }
    request.onload = () =>
      request.status >= 200 && request.status < 300
        ? resolve()
        : reject(new ApiError(request.status, 'The file could not be stored', `Storage answered ${request.status}`))
    request.onerror = () => reject(new ApiError(0, 'The file could not be stored', 'Storage could not be reached'))
    request.send(file)
  })
}
