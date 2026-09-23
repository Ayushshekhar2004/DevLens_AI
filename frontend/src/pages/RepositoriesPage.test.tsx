import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RepositoriesPage } from './RepositoriesPage'
import { session } from '../test/fixtures'
import * as api from '../services/repositoryApi'

vi.mock('../services/repositoryApi')
const inventory = vi.mocked(api.getRepositoryInventory)
const detail = vi.mocked(api.getRepositoryDetail)
const importer = vi.mocked(api.importRepository)
const remove = vi.mocked(api.deleteRepository)

const summary = { snapshotId: 4, sourceName: 'mixed.zip', createdAt: '2026-09-23T10:00:00Z', totalBytes: 120,
  acceptedFileCount: 5, importStatus: 'COMPLETED', scanStatus: 'COMPLETED', detectedStack: 'Java, React',
  includedCount: 3, skippedCount: 2, skipReasons: { IGNORE_RULE: 1, GENERATED_OR_MINIFIED: 1 }, parserCoverage: 100 }

describe('RepositoriesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    inventory.mockResolvedValue({ content: [summary], page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true })
    detail.mockResolvedValue({ summary, modules: [{ name: 'backend', rootPath: 'backend', type: 'JAVA_MAVEN', manifestPath: 'backend/pom.xml' }], files: { content: [{ relativePath: 'backend/src/Main.java', language: 'JAVA', contentHash: 'a'.repeat(64), lineCount: 3, parserStatus: 'PARSED', parserMode: 'HEURISTIC', moduleRoot: 'backend' }], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true } })
  })

  it('renders bounded inventory metadata and loads details without source bodies', async () => {
    render(<RepositoriesPage session={session} onDashboard={vi.fn()} onHistory={vi.fn()} onAnalytics={vi.fn()} onLogout={vi.fn()} onSessionExpired={vi.fn()} />)
    expect(await screen.findByText('mixed.zip')).toBeInTheDocument()
    fireEvent.click(screen.getByText('mixed.zip'))
    expect(await screen.findByText('backend/src/Main.java')).toBeInTheDocument()
    expect(screen.getByText('100%')).toBeInTheDocument()
    expect(screen.queryByText(/class Main/)).not.toBeInTheDocument()
  })

  it('validates uploads, reports completed scans, and deletes after confirmation', async () => {
    importer.mockResolvedValue({ snapshotId: 4,
      importJob: { id: 1, type: 'IMPORT', status: 'COMPLETED', snapshotId: 4, sourceName: 'mixed.zip', errorMessage: null, createdAt: '', updatedAt: '' },
      scanJob: { id: 2, type: 'SCAN', status: 'COMPLETED', snapshotId: 4, sourceName: 'mixed.zip', errorMessage: null, createdAt: '', updatedAt: '' } })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<RepositoriesPage session={session} onDashboard={vi.fn()} onHistory={vi.fn()} onAnalytics={vi.fn()} onLogout={vi.fn()} onSessionExpired={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Import and scan' }))
    expect(await screen.findByText('Choose a ZIP file to import.')).toBeInTheDocument()
    const file = new File(['fixture'], 'mixed.zip', { type: 'application/zip' })
    fireEvent.change(screen.getByLabelText('Repository ZIP'), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: 'Import and scan' }))
    expect(await screen.findByText('Scan status: COMPLETED')).toBeInTheDocument()
    await waitFor(() => expect(importer).toHaveBeenCalledWith(file, 'test-token'))
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await waitFor(() => expect(remove).toHaveBeenCalledWith(4, 'test-token'))
  })
})
