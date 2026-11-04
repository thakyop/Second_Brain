import { useRef, useState, type DragEvent } from 'react'
import { ACCEPTED_FILES } from '../lib/uploadRules.ts'

interface Props {
  onFiles: (files: File[]) => void
  /** The name of the file being uploaded, if any. */
  uploading: string | null
}

/** Drop files here, or pick them with the file chooser. */
export function UploadDropzone({ onFiles, uploading }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const busy = uploading !== null

  function handleDragOver(event: DragEvent) {
    event.preventDefault()
    if (!busy) {
      setDragging(true)
    }
  }

  function handleDragLeave(event: DragEvent) {
    // Moving onto a child element also fires dragleave; ignore that.
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
      setDragging(false)
    }
  }

  function handleDrop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    if (!busy) {
      onFiles(Array.from(event.dataTransfer.files))
    }
  }

  return (
    <div
      className={`dropzone${dragging ? ' dropzone-active' : ''}${busy ? ' dropzone-busy' : ''}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {busy ? (
        <p className="dropzone-title" role="status">
          <span className="spinner" aria-hidden="true" /> Uploading {uploading}…
        </p>
      ) : (
        <>
          <svg className="dropzone-icon" viewBox="0 0 24 24" width="28" height="28" aria-hidden="true">
            <path d="M12 16V4m0 0-4.5 4.5M12 4l4.5 4.5M4 16v2.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V16"
              fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <p className="dropzone-title">
            Drop files here or{' '}
            <button type="button" className="link-button" onClick={() => inputRef.current?.click()}>
              choose files
            </button>
          </p>
          <p className="dropzone-hint">PDF, .txt or .md, up to 20 MB each</p>
        </>
      )}
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ACCEPTED_FILES}
        hidden
        aria-label="Choose files to upload"
        onChange={(event) => {
          onFiles(Array.from(event.target.files ?? []))
          event.target.value = '' // so choosing the same file again still triggers onChange
        }}
      />
    </div>
  )
}
