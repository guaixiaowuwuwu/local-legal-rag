<template>
  <section>
    <div class="toolbar">
      <h1>知识库</h1>
      <el-button type="primary" class="icon-button" @click="submitCreate">
        <Plus />
        新建
      </el-button>
    </div>

    <div class="work-grid">
      <div class="panel">
        <div class="panel-title">
          <h2>新建知识库</h2>
        </div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="名称">
            <el-input v-model="form.name" maxlength="120" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" />
          </el-form-item>
        </el-form>
      </div>

      <div class="panel">
        <div class="panel-title">
          <h2>列表</h2>
          <el-button :loading="store.loading" class="icon-button" @click="store.load">
            <RefreshCw />
            刷新
          </el-button>
        </div>
        <el-table v-loading="store.loading" :data="store.items" row-key="id">
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="documentCount" label="文档" width="90" />
          <el-table-column prop="chunkCount" label="片段" width="90" />
          <el-table-column label="任务" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.latestJobStatus" size="small" effect="plain">
                {{ row.latestJobStatus }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column width="110" align="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="router.push(`/knowledge-bases/${row.id}`)">
                打开
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!store.loading && !store.hasItems" description="暂无知识库" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, RefreshCw } from '@lucide/vue'
import { useKnowledgeBaseStore } from '../stores/knowledgeBases'

const router = useRouter()
const store = useKnowledgeBaseStore()
const form = reactive({ name: '', description: '' })

onMounted(() => store.load())

async function submitCreate() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入知识库名称。')
    return
  }
  try {
    const created = await store.create(form.name.trim(), form.description.trim())
    form.name = ''
    form.description = ''
    await router.push(`/knowledge-bases/${created.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建失败')
  }
}
</script>
