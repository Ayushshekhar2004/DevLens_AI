export type RepositoryJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface RepositoryJob {
  id: number
  type: 'IMPORT' | 'SCAN'
  status: RepositoryJobStatus
  snapshotId: number | null
  sourceName: string
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface RepositoryImportResult { snapshotId: number; importJob: RepositoryJob; scanJob: RepositoryJob }
export interface RepositoryModule { name: string; rootPath: string; type: string; manifestPath: string | null }
export interface RepositoryFile { relativePath: string; language: string; contentHash: string; lineCount: number; parserStatus: string; parserMode: string; moduleRoot: string }
export interface RepositorySummary {
  snapshotId: number; sourceName: string; createdAt: string; totalBytes: number; acceptedFileCount: number
  importStatus: string; scanStatus: string; detectedStack: string; includedCount: number; skippedCount: number
  skipReasons: Record<string, number>; parserCoverage: number
}
export interface RepositoryPage<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export interface RepositoryDetail { summary: RepositorySummary; modules: RepositoryModule[]; files: RepositoryPage<RepositoryFile> }
