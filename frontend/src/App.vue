<template>
  <el-container class="app-shell">
    <el-header class="topbar">
      <router-link class="brand" to="/">
        <Scale class="brand-icon" />
        <span>法律检索 RAG</span>
      </router-link>
      <el-tag v-if="config" effect="plain" type="info">
        {{ config.chatModel }} / {{ config.embeddingModel }}
      </el-tag>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Scale } from '@lucide/vue'
import { getRuntimeConfig, type RuntimeConfig } from './api/client'

const config = ref<RuntimeConfig | null>(null)

onMounted(async () => {
  try {
    config.value = await getRuntimeConfig()
  } catch {
    config.value = null
  }
})
</script>
