<template>
  <div class="space-y-6">
    <div class="rounded-2xl bg-gradient-to-r from-primary via-primary-light to-primary-lighter p-7 text-white shadow-lg shadow-indigo-200 relative overflow-hidden">
      <div class="absolute -right-8 -top-8 w-48 h-48 rounded-full bg-white/10"></div>
      <div class="absolute right-16 bottom-0 w-24 h-24 rounded-full bg-white/10"></div>
      <h1 class="text-2xl font-semibold mb-1.5">今天也要坚持刷题哦</h1>
      <p class="text-indigo-100 text-sm">软考备考，从每一天的练习开始。已收录 {{ stats?.overview?.totalQuestions || 0 }} 道题目。</p>
      <el-button class="mt-4" type="warning" round @click="$router.push('/practice')">
        <PencilLine class="w-4 h-4 mr-1.5" /> 开始刷题
      </el-button>
    </div>
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div v-for="card in cards" :key="card.label" class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 card-hover">
        <div class="flex items-center justify-between mb-3">
          <span class="text-sm text-gray-500">{{ card.label }}</span>
          <div class="w-9 h-9 rounded-xl flex items-center justify-center" :class="card.bg">
            <component :is="card.icon" class="w-4.5 h-4.5" :class="card.fg" />
          </div>
        </div>
        <div class="text-2xl font-bold text-gray-900">{{ card.value }}</div>
        <div class="text-xs text-gray-400 mt-1">{{ card.sub }}</div>
      </div>
    </div>
    <div class="grid lg:grid-cols-2 gap-4">
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
        <div class="flex items-center justify-between mb-4">
          <h3 class="font-semibold text-gray-800">科目进度</h3>
          <el-button link type="primary" @click="$router.push('/stats')">查看统计</el-button>
        </div>
        <div v-if="subjectProgress.length" class="space-y-4">
          <div v-for="s in subjectProgress" :key="s.subjectId">
            <div class="flex justify-between text-sm mb-1.5">
              <span class="text-gray-700 font-medium">{{ s.subjectName }}</span>
              <span class="text-gray-400 text-xs">{{ s.answeredQuestions }} / {{ s.totalQuestions }} 题 · 正确率 {{ s.accuracy }}%</span>
            </div>
            <el-progress :percentage="s.progress" :stroke-width="10" :color="progressColor" />
          </div>
        </div>
        <el-empty v-else description="暂无数据，先去导入题库吧" :image-size="80" />
      </div>
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
        <h3 class="font-semibold text-gray-800 mb-4">近 7 天练习</h3>
        <div v-if="weekTrend.length" class="flex items-end justify-between gap-2 h-44">
          <div v-for="d in weekTrend" :key="d.date" class="flex-1 flex flex-col items-center gap-1.5 group">
            <span class="text-xs text-gray-500 font-medium">{{ d.answered || '' }}</span>
            <div class="w-full max-w-8 rounded-t-lg bg-gradient-to-t from-primary-lighter to-primary transition-all duration-300 group-hover:opacity-80"
              :style="{ height: barHeight(d.answered) + '%' }" :class="d.answered ? '' : 'bg-gray-100 min-h-1'"></div>
            <span class="text-[10px] text-gray-400">{{ d.date.slice(5) }}</span>
          </div>
        </div>
        <el-empty v-else description="还没有练习记录" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'Dashboard' })

import { computed, onActivated, onMounted, ref } from 'vue'
import api from '../api'
import { useAppStore } from '../stores/app'
import { PencilLine, Database, CheckCircle2, Target, AlertCircle, BookOpen } from '@lucide/vue'

const store = useAppStore()
const stats = ref(null)

onMounted(async () => {
  await store.loadSubjects(true).catch(() => {})
  stats.value = await api.stats()
})

onActivated(async () => {
  await store.loadSubjects(true).catch(() => {})
  stats.value = await api.stats().catch(() => stats.value)
})

const cards = computed(() => {
  const o = stats.value?.overview || {}
  return [
    { label: '题目总数', value: o.totalQuestions || 0, sub: '题库收录', icon: Database, bg: 'bg-indigo-50', fg: 'text-primary' },
    { label: '累计刷题', value: o.totalAnswered || 0, sub: '今日 ' + (o.todayAnswered || 0) + ' 题', icon: PencilLine, bg: 'bg-blue-50', fg: 'text-blue-500' },
    { label: '总体正确率', value: (o.accuracy || 0) + '%', sub: '今日 ' + (o.todayCorrect || 0) + ' 题正确', icon: CheckCircle2, bg: 'bg-emerald-50', fg: 'text-emerald-500' },
    { label: '错题待消灭', value: o.wrongCount || 0, sub: '错题本收录', icon: AlertCircle, bg: 'bg-red-50', fg: 'text-red-500' }
  ]
})

const subjectProgress = computed(() => stats.value?.subjectProgress || [])
const weekTrend = computed(() => (stats.value?.dailyTrend || []).slice(-7))

function barHeight(v) {
  const max = Math.max(...weekTrend.value.map(d => d.answered), 1)
  return Math.max((v / max) * 100, 4)
}
const progressColor = '#4F46E5'
</script>
