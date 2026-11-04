import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { describeError } from '../api/client.ts'
import { uploadDocument } from '../api/endpoints.ts'
import type { DocumentInfo } from '../api/types.ts'
import { useToken } from '../auth/AuthContext.ts'
import { BackToWorkspaces } from '../components/BackToWorkspaces.tsx'
import { DocumentList } from '../components/DocumentList.tsx'
import { UploadDropzone } from '../components/UploadDropzone.tsx'
import { WorkspaceTabs } from '../components/WorkspaceTabs.tsx'
import { isPending, useDocuments } from '../hooks/useDocuments.ts'
import { useWorkspace } from '../hooks/useWorkspace.ts'
import { uploadProblem } from '../lib/uploadRules.ts'

/** One workspace: upload documents and watch them being processed. */
export function WorkspacePage() {
  const { workspaceId = '' } = useParams()
  // A new key per workspace resets the page's state when you switch workspaces.
  return <WorkspaceView key={workspaceId} workspaceId={workspaceId} />
}

interface UploadIssue {
  filename: string
  message: string
}

function WorkspaceView({ workspaceId }: { workspaceId: string }) {
  const token = useToken()
  const workspace = useWorkspace(token, workspaceId)
  const { documents, error, addDocument, reload } = useDocuments(token, workspaceId)
  const [uploading, setUploading] = useState<string | null>(null)
  const [issues, setIssues] = useState<UploadIssue[]>([])

  // The endpoint takes one file per request, so files go up one at a time.
  async function handleFiles(files: File[]) {
    const found: UploadIssue[] = []
    for (const file of files) {
      const problem = uploadProblem(file)
      if (problem) {
        found.push({ filename: file.name, message: problem })
        continue
      }
      setUploading(file.name)
      try {
        addDocument(await uploadDocument(token, workspaceId, file))
      } catch (uploadError) {
        found.push({ filename: file.name, message: uploadErrorText(uploadError) })
      }
    }
    setUploading(null)
    setIssues(found)
  }

  if (documents === null && error) {
    return (
      <>
        <BackToWorkspaces />
        <div className="state-box state-error" role="alert">
          <p>{pageErrorText(error)}</p>
          <Link to="/workspaces" className="button">Back to workspaces</Link>
        </div>
      </>
    )
  }

  return (
    <>
      <BackToWorkspaces />
      <div className="page-header">
        <h1>{workspace?.name ?? 'Workspace'}</h1>
        {documents && <p className="page-subtitle">{summary(documents)}</p>}
      </div>
      <WorkspaceTabs workspaceId={workspaceId} />

      <UploadDropzone onFiles={handleFiles} uploading={uploading} />

      {issues.length > 0 && (
        <div className="banner banner-error" role="alert">
          <ul className="issue-list">
            {issues.map((issue, index) => (
              <li key={index}><strong>{issue.filename}</strong>: {issue.message}</li>
            ))}
          </ul>
          <button type="button" className="link-button" onClick={() => setIssues([])}>Dismiss</button>
        </div>
      )}

      {documents && error ? (
        <div className="banner banner-error" role="alert">
          <span>Couldn't refresh the list. {describeError(error)}</span>
          <button type="button" className="link-button" onClick={reload}>Try again</button>
        </div>
      ) : null}

      <section className="documents" aria-labelledby="documents-heading">
        <h2 id="documents-heading" className="section-title">Documents</h2>
        {documents === null ? (
          <p className="state-box" role="status">
            <span className="spinner" aria-hidden="true" /> Loading documents…
          </p>
        ) : documents.length === 0 ? (
          <div className="state-box">
            <p className="state-title">No documents yet</p>
            <p>Upload a file above. The backend reads its text, splits it into chunks and stores an embedding for each one.</p>
          </div>
        ) : (
          <DocumentList documents={documents} />
        )}
      </section>
    </>
  )
}

/** "3 documents · 2 ready · 1 processing" */
function summary(documents: DocumentInfo[]): string {
  if (documents.length === 0) {
    return 'No documents yet'
  }
  const parts = [`${documents.length} ${documents.length === 1 ? 'document' : 'documents'}`]
  const ready = documents.filter((d) => d.status === 'READY').length
  const pending = documents.filter(isPending).length
  const failed = documents.filter((d) => d.status === 'FAILED').length
  if (ready) parts.push(`${ready} ready`)
  if (pending) parts.push(`${pending} processing`)
  if (failed) parts.push(`${failed} failed`)
  return parts.join(' · ')
}

function pageErrorText(error: unknown): string {
  return describeError(error, {
    400: "This workspace doesn't exist.", // the id in the address isn't a valid UUID
    403: "You're not a member of this workspace.",
    404: "This workspace doesn't exist.",
  })
}

function uploadErrorText(error: unknown): string {
  return describeError(error, {
    400: "The server didn't accept this file. Upload a PDF, .txt or .md file that isn't empty.",
    403: "You're not a member of this workspace.",
    404: 'This workspace no longer exists.',
    413: 'This file is larger than 20 MB.',
  })
}
