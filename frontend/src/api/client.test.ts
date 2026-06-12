import { afterEach, describe, expect, it, vi } from 'vitest'
import { createKnowledgeBase, listKnowledgeBases } from './client'

describe('api client', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('loads knowledge bases', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([
      {
        id: 'kb-1',
        name: '劳动用工',
        description: '',
        documentCount: 2,
        chunkCount: 12,
        latestJobStatus: 'SUCCEEDED',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]))))

    const result = await listKnowledgeBases()

    expect(result).toHaveLength(1)
    expect(result[0].name).toBe('劳动用工')
  })

  it('sends JSON for create requests', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      id: 'kb-1',
      name: '合同审查',
      description: '内部资料',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    })))
    vi.stubGlobal('fetch', fetchMock)

    await createKnowledgeBase({ name: '合同审查', description: '内部资料' })

    const firstCall = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const [, init] = firstCall
    expect(init?.method).toBe('POST')
    expect((init?.headers as Headers).get('Content-Type')).toBe('application/json')
    expect(init?.body).toContain('合同审查')
  })
})
