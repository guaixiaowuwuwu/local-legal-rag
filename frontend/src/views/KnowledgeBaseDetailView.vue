<template>
  <section class="stack">
    <div class="toolbar">
      <div>
        <h1>{{ kb?.name ?? '知识库' }}</h1>
        <span class="source-meta">{{ kb?.description }}</span>
      </div>
      <el-button class="icon-button" @click="router.push('/')">
        <ArrowLeft />
        返回
      </el-button>
    </div>

    <div class="metrics">
      <div class="metric">
        <div class="metric-label">文档</div>
        <div class="metric-value">{{ kb?.documentCount ?? '-' }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">片段</div>
        <div class="metric-value">{{ kb?.chunkCount ?? '-' }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">任务</div>
        <div class="metric-value">{{ activeJob?.status ?? kb?.latestJobStatus ?? '-' }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">Top-K</div>
        <div class="metric-value">{{ topK }}</div>
      </div>
    </div>

    <div class="work-grid">
      <div class="stack">
        <div class="panel">
          <div class="panel-title">
            <h2>文档</h2>
            <el-button :loading="loadingDocs" class="icon-button" @click="loadDocuments">
              <RefreshCw />
              刷新
            </el-button>
          </div>
          <el-upload
            v-model:file-list="fileList"
            drag
            multiple
            :auto-upload="false"
            :limit="20"
            accept=".pdf,.docx,.md,.markdown,.txt"
          >
            <UploadCloud class="upload-icon" />
            <div>拖放文件或点击选择</div>
          </el-upload>
          <div class="panel-title upload-actions">
            <span class="source-meta">{{ fileList.length }} 个待上传文件</span>
            <el-button type="primary" :loading="uploading" class="icon-button" @click="submitUpload">
              <Upload />
              上传
            </el-button>
          </div>
          <el-table :data="documents" size="small" row-key="id">
            <el-table-column prop="originalFilename" label="文件" min-width="180" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small" effect="plain">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100">
              <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
            </el-table-column>
            <el-table-column width="70" align="right">
              <template #default="{ row }">
                <el-button type="danger" link @click="removeDocument(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="panel">
          <div class="panel-title">
            <h2>建库</h2>
            <el-button type="primary" :loading="ingesting" class="icon-button" @click="startIngest">
              <DatabaseZap />
              开始
            </el-button>
          </div>
          <el-progress
            v-if="activeJob"
            :percentage="jobProgress"
            :status="activeJob.status === 'FAILED' ? 'exception' : activeJob.status === 'SUCCEEDED' ? 'success' : undefined"
          />
          <div v-if="activeJob" class="source-meta">
            {{ activeJob.processedDocuments }}/{{ activeJob.totalDocuments }} 文档，
            {{ activeJob.totalChunks }} 片段
            <span v-if="activeJob.errorMessage">，{{ activeJob.errorMessage }}</span>
          </div>
        </div>
      </div>

      <div class="stack">
        <div class="panel">
          <div class="panel-title">
            <h2>问答</h2>
            <el-input-number v-model="topK" :min="1" :max="20" size="small" />
          </div>
          <el-input v-model="question" type="textarea" :rows="5" />
          <div class="panel-title upload-actions">
            <span class="source-meta">{{ config?.apiKeyConfigured ? 'API 已配置' : 'API Key 未配置' }}</span>
            <el-button type="primary" :loading="asking" class="icon-button" @click="submitQuestion">
              <Search />
              检索并回答
            </el-button>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">
            <h2>回答</h2>
          </div>
          <div v-if="answer" class="answer">{{ answer.answer }}</div>
          <el-empty v-else description="暂无回答" />
        </div>

        <div v-if="answer?.sources.length" class="panel">
          <div class="panel-title">
            <h2>来源</h2>
          </div>
          <div class="source-list">
            <div v-for="source in answer.sources" :key="source.chunkId" class="source-card">
              <div class="source-head">
                <span>{{ source.id }} · {{ source.source }}</span>
                <el-tag size="small" effect="plain">{{ source.score.toFixed(4) }}</el-tag>
              </div>
              <div class="source-meta">
                <span>{{ source.page ? `第${source.page}页` : '无页码' }}</span>
                <span> · {{ source.article ?? '未标注法条' }}</span>
              </div>
              <div class="preview">{{ source.preview }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type UploadUserFile } from 'element-plus'
import {
  ArrowLeft,
  DatabaseZap,
  RefreshCw,
  Search,
  Upload,
  UploadCloud,
} from '@lucide/vue'
import {
  askQuestion,
  createIngestJob,
  deleteDocument,
  getIngestJob,
  getKnowledgeBase,
  getRuntimeConfig,
  listDocuments,
  uploadDocuments,
  type IngestJob,
  type KnowledgeBaseSummary,
  type QuestionResponse,
  type RuntimeConfig,
  type StoredDocument,
} from '../api/client'

const route = useRoute()
const router = useRouter()
const knowledgeBaseId = computed(() => String(route.params.id))

const kb = ref<KnowledgeBaseSummary | null>(null)
const documents = ref<StoredDocument[]>([])
const config = ref<RuntimeConfig | null>(null)
const fileList = ref<UploadUserFile[]>([])
const activeJob = ref<IngestJob | null>(null)
const answer = ref<QuestionResponse | null>(null)
const question = ref('试用期工资有什么要求？')
const topK = ref(5)
const loadingDocs = ref(false)
const uploading = ref(false)
const ingesting = ref(false)
const asking = ref(false)
let pollTimer: number | undefined

const jobProgress = computed(() => {
  const job = activeJob.value
  if (!job || job.totalDocuments === 0) return 0
  return Math.round((job.processedDocuments / job.totalDocuments) * 100)
})

onMounted(async () => {
  await Promise.all([loadKnowledgeBase(), loadDocuments(), loadConfig()])
})

onBeforeUnmount(() => stopPolling())

async function loadKnowledgeBase() {
  kb.value = await getKnowledgeBase(knowledgeBaseId.value)
}

async function loadDocuments() {
  loadingDocs.value = true
  try {
    documents.value = await listDocuments(knowledgeBaseId.value)
  } finally {
    loadingDocs.value = false
  }
}

async function loadConfig() {
  config.value = await getRuntimeConfig()
  topK.value = config.value.topK
}

async function submitUpload() {
  const files = fileList.value
    .map((item) => item.raw)
    .filter((file): file is NonNullable<UploadUserFile['raw']> => Boolean(file))
  if (!files.length) {
    ElMessage.warning('请选择文件。')
    return
  }
  uploading.value = true
  try {
    await uploadDocuments(knowledgeBaseId.value, files)
    fileList.value = []
    await Promise.all([loadKnowledgeBase(), loadDocuments()])
    ElMessage.success('上传完成。')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

async function removeDocument(documentId: string) {
  try {
    await deleteDocument(knowledgeBaseId.value, documentId)
    await Promise.all([loadKnowledgeBase(), loadDocuments()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function startIngest() {
  ingesting.value = true
  try {
    activeJob.value = await createIngestJob(knowledgeBaseId.value)
    pollJob(activeJob.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '建库失败')
    ingesting.value = false
  }
}

function pollJob(jobId: string) {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    activeJob.value = await getIngestJob(jobId)
    if (activeJob.value.status === 'SUCCEEDED' || activeJob.value.status === 'FAILED') {
      stopPolling()
      ingesting.value = false
      await Promise.all([loadKnowledgeBase(), loadDocuments()])
    }
  }, 1500)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function submitQuestion() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题。')
    return
  }
  asking.value = true
  try {
    answer.value = await askQuestion(knowledgeBaseId.value, question.value.trim(), topK.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '问答失败')
  } finally {
    asking.value = false
  }
}

function statusType(status: StoredDocument['status']) {
  if (status === 'INDEXED') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.upload-icon {
  width: 28px;
  height: 28px;
  color: #1f7a68;
}

.upload-actions {
  margin-top: 12px;
}
</style>
