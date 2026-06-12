const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

export interface KnowledgeBaseSummary {
  id: string
  name: string
  description: string
  documentCount: number
  chunkCount: number
  latestJobStatus: IngestJobStatus | null
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBase {
  id: string
  name: string
  description: string
  createdAt: string
  updatedAt: string
}

export type DocumentStatus = 'UPLOADED' | 'INDEXED' | 'FAILED'
export type IngestJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface StoredDocument {
  id: string
  knowledgeBaseId: string
  originalFilename: string
  storedFilename: string
  contentType: string | null
  sizeBytes: number
  status: DocumentStatus
  errorMessage: string | null
  uploadedAt: string
}

export interface IngestJob {
  id: string
  knowledgeBaseId: string
  status: IngestJobStatus
  resetIndex: boolean
  totalDocuments: number
  processedDocuments: number
  totalChunks: number
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface RuntimeConfig {
  apiBaseUrl: string
  chatModel: string
  embeddingModel: string
  embeddingDimensions: number
  chunkSize: number
  chunkOverlap: number
  topK: number
  apiKeyConfigured: boolean
}

export interface SourceResponse {
  id: string
  chunkId: string
  documentId: string
  label: string
  source: string
  sourcePath: string
  page: number | null
  article: string | null
  score: number
  preview: string
}

export interface RetrievedChunkResponse {
  id: string
  documentId: string
  chunkIndex: number
  content: string
  source: string
  page: number | null
  article: string | null
  score: number
}

export interface QuestionResponse {
  answer: string
  sources: SourceResponse[]
  retrievedChunks: RetrievedChunkResponse[]
}

export async function listKnowledgeBases(): Promise<KnowledgeBaseSummary[]> {
  return request('/knowledge-bases')
}

export async function createKnowledgeBase(payload: {
  name: string
  description: string
}): Promise<KnowledgeBase> {
  return request('/knowledge-bases', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function getKnowledgeBase(id: string): Promise<KnowledgeBaseSummary> {
  return request(`/knowledge-bases/${id}`)
}

export async function listDocuments(knowledgeBaseId: string): Promise<StoredDocument[]> {
  return request(`/knowledge-bases/${knowledgeBaseId}/documents`)
}

export async function uploadDocuments(
  knowledgeBaseId: string,
  files: File[],
): Promise<StoredDocument[]> {
  const form = new FormData()
  for (const file of files) {
    form.append('files', file)
  }
  return request(`/knowledge-bases/${knowledgeBaseId}/documents`, {
    method: 'POST',
    body: form,
    skipJsonHeader: true,
  })
}

export async function deleteDocument(knowledgeBaseId: string, documentId: string): Promise<void> {
  await request(`/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`, {
    method: 'DELETE',
  })
}

export async function createIngestJob(knowledgeBaseId: string): Promise<IngestJob> {
  return request(`/knowledge-bases/${knowledgeBaseId}/ingest-jobs`, {
    method: 'POST',
    body: JSON.stringify({ resetIndex: true }),
  })
}

export async function getIngestJob(jobId: string): Promise<IngestJob> {
  return request(`/ingest-jobs/${jobId}`)
}

export async function askQuestion(
  knowledgeBaseId: string,
  question: string,
  topK: number,
): Promise<QuestionResponse> {
  return request(`/knowledge-bases/${knowledgeBaseId}/questions`, {
    method: 'POST',
    body: JSON.stringify({ question, topK }),
  })
}

export async function getRuntimeConfig(): Promise<RuntimeConfig> {
  return request('/runtime-config')
}

interface RequestInitWithFlags extends RequestInit {
  skipJsonHeader?: boolean
}

async function request<T>(path: string, options: RequestInitWithFlags = {}): Promise<T> {
  const { skipJsonHeader, ...fetchOptions } = options
  const headers = new Headers(options.headers)
  if (!skipJsonHeader && options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(`${API_BASE}${path}`, {
    ...fetchOptions,
    headers,
  })
  if (!response.ok) {
    const message = await errorMessage(response)
    throw new Error(message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const payload = await response.json()
    return payload.message ?? response.statusText
  } catch {
    return response.statusText
  }
}
