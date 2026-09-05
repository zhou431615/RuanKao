<template>
  <div class="space-y-4">
    <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 flex flex-wrap items-center gap-3">
      <el-select v-model="subjectId" placeholder="全部科目" clearable class="!w-48" @change="load">
        <el-option v-for="s in store.subjects" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <div class="flex-1"></div>
      <el-button type="primary" plain :disabled="!list.length" @click="practiceFav">
        <Play class="w-4 h-4 mr-1.5" /> 练习收藏
      </el-button>
    </div>
    <div v-loading="loading" class="space-y-3 min-h-40">
      <QuestionCard v-for="q in list" :key="q.id" :question="q" @toggle-favorite="toggleFavorite">
        <div class="mt-3">
          <el-button size="small" text type="danger" @click="remove(q)">取消收藏</el-button>
        </div>
      </QuestionCard>
      <EmptyState v-if="!list.length && !loading" title="还没有收藏的题目"
        description="刷题或浏览题库时点击右上角星标即可收藏，方便后续集中复习。">
        <template #icon><Star class="w-7 h-7" /></template>
        <template #action>
          <el-button size="small" type="primary" @click="goPractice">
            <Play class="w-4 h-4 mr-1" /> 去刷题
          </el-button>
          <el-button size="small" @click="goBank">
            <Library class="w-4 h-4 mr-1" /> 浏览题库
          </el-button>
        </template>
      </EmptyState>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'Favorites' })

import { onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Play, Star, Library } from '@lucide/vue'
import api from '../api'
import { useAppStore } from '../stores/app'
import QuestionCard from '../components/QuestionCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { notify, withLoading } from '../utils/feedback'

const store = useAppStore()
const router = useRouter()
const list = ref([])
const loading = ref(false)
const subjectId = ref(null)

onMounted(async () => {
  await store.loadSubjects().catch(() => {})
  load()
})

onActivated(async () => {
  await store.loadSubjects().catch(() => {})
  load()
})

async function load() {
  await withLoading(loading, async () => {
    list.value = await api.favoriteList(subjectId.value ? { subjectId: subjectId.value } : undefined)
  })
}

async function remove(q) {
  await api.removeFavorite(q.id)
  notify.success('已取消收藏')
  await load()
}

async function toggleFavorite(q) {
  try {
    if (q.favorite) {
      await api.removeFavorite(q.id)
      q.favorite = false
    } else {
      await api.addFavorite(q.id)
      q.favorite = true
      notify.success('已收藏')
    }
  } catch {
    // 拦截器已给出提示
  }
}

function practiceFav() {
  router.push({ path: '/practice', query: { source: 'favorite', subjectId: subjectId.value || undefined } })
}

function goPractice() {
  router.push('/practice')
}

function goBank() {
  router.push('/bank')
}
</script>
