<template>
  <div class="space-y-4">
    <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 flex flex-wrap items-center gap-3">
      <el-select v-model="subjectId" placeholder="全部科目" clearable class="!w-48" @change="load">
        <el-option v-for="s in store.subjects" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <div class="flex-1"></div>
      <el-button type="primary" plain :disabled="!list.length" @click="practiceWrong">
        <RotateCcw class="w-4 h-4 mr-1.5" /> 重练错题
      </el-button>
      <el-button type="danger" plain :disabled="!list.length" @click="clearAll">清空</el-button>
    </div>
    <div v-loading="loading" class="space-y-3 min-h-40">
      <QuestionCard v-for="q in list" :key="q.id" :question="q"
        @toggle-favorite="toggleFavorite">
        <div class="mt-3">
          <el-button size="small" text type="danger" @click="remove(q)">从错题本移除</el-button>
        </div>
      </QuestionCard>
      <EmptyState v-if="!list.length && !loading" title="太棒了，当前没有错题"
        description="答错的题目会自动收进错题本。继续保持，或去题库里挑一批题练练手。">
        <template #icon><PartyPopper class="w-7 h-7" /></template>
        <template #action>
          <el-button size="small" type="primary" @click="goPractice">
            <Play class="w-4 h-4 mr-1" /> 去刷题
          </el-button>
        </template>
      </EmptyState>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'WrongBook' })

import { onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RotateCcw, PartyPopper, Play } from '@lucide/vue'
import api from '../api'
import { useAppStore } from '../stores/app'
import QuestionCard from '../components/QuestionCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { notify, confirmAction, withLoading } from '../utils/feedback'

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
    list.value = await api.wrongList(subjectId.value ? { subjectId: subjectId.value } : undefined)
  })
}

async function remove(q) {
  await api.removeWrong(q.id)
  notify.success('已从错题本移除')
  await load()
}

async function clearAll() {
  const count = list.value.length
  try {
    await confirmAction(`确定清空这 ${count} 道错题吗？清空后无法恢复。`, { title: '清空错题本' })
  } catch {
    return
  }
  await api.clearWrong(subjectId.value ? { subjectId: subjectId.value } : {})
  notify.success(`已清空 ${count} 道错题`)
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
      notify.success('已收藏，可在收藏夹中查看')
    }
  } catch {
    // 拦截器已给出提示
  }
}

function practiceWrong() {
  router.push({ path: '/practice', query: { source: 'wrong', subjectId: subjectId.value || undefined } })
}

function goPractice() {
  router.push('/practice')
}
</script>
