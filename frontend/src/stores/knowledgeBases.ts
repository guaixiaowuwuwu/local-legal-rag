import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createKnowledgeBase,
  listKnowledgeBases,
  type KnowledgeBaseSummary,
} from '../api/client'

export const useKnowledgeBaseStore = defineStore('knowledgeBases', () => {
  const items = ref<KnowledgeBaseSummary[]>([])
  const loading = ref(false)

  const hasItems = computed(() => items.value.length > 0)

  async function load() {
    loading.value = true
    try {
      items.value = await listKnowledgeBases()
    } finally {
      loading.value = false
    }
  }

  async function create(name: string, description: string) {
    const created = await createKnowledgeBase({ name, description })
    await load()
    return created
  }

  return { items, loading, hasItems, load, create }
})
