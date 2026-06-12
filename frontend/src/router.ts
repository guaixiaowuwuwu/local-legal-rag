import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeBaseListView from './views/KnowledgeBaseListView.vue'
import KnowledgeBaseDetailView from './views/KnowledgeBaseDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'knowledge-bases', component: KnowledgeBaseListView },
    { path: '/knowledge-bases/:id', name: 'knowledge-base-detail', component: KnowledgeBaseDetailView },
  ],
})

export default router
