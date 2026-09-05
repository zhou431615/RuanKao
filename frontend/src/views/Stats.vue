<template>
  <div class="space-y-5">
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div v-for="c in cards" :key="c.label" class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 card-hover">
        <div class="text-sm text-gray-500 mb-2">{{ c.label }}</div>
        <div class="text-2xl font-bold" :class="c.color">{{ c.value }}</div>
      </div>
    </div>

    <div class="grid lg:grid-cols-5 gap-5">
      <div class="lg:col-span-3 bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
        <h3 class="font-semibold text-gray-800 mb-4">近 30 天练习趋势</h3>
        <v-chart :option="trendOption" class="h-72" autoresize />
      </div>
      <div class="lg:col-span-2 bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
        <h3 class="font-semibold text-gray-800 mb-4">答题正确率</h3>
        <v-chart :option="pieOption" class="h-72" autoresize />
      </div>
    </div>

    <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
      <h3 class="font-semibold text-gray-800 mb-4">科目学习进度</h3>
      <el-table :data="subjectRows" style="width: 100%" :header-cell-style="{ background: '#F8FAFF', color: '#475569' }">
        <el-table-column prop="subjectName" label="科目" min-width="160" />
        <el-table-column prop="totalQuestions" label="题库题数" width="100" />
        <el-table-column prop="totalAnswered" label="累计答题" width="100" />
        <el-table-column label="正确率" width="120">
          <template #default="{ row }">
            <span :class="row.accuracy >= 80 ? 'text-emerald-500' : row.accuracy >= 60 ? 'text-amber-500' : 'text-red-500'" class="font-medium">
              {{ row.accuracy }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column label="刷题进度" min-width="220">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="10" color="#4F46E5" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!subjectRows.length" description="暂无数据" :image-size="80" />
    </div>

    <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
      <div class="flex items-center justify-between mb-4">
        <h3 class="font-semibold text-gray-800">最近答题记录</h3>
        <el-pagination v-if="historyTotal > 10" small background layout="prev, pager, next" :total="historyTotal"
          :page-size="10" :current-page="historyPage" @current-change="loadHistory" />
      </div>
      <el-table :data="history" style="width: 100%" :header-cell-style="{ background: '#F8FAFF', color: '#475569' }">
        <el-table-column prop="stem" label="题干" min-width="280" show-overflow-tooltip />
        <el-table-column label="题型" width="90">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="userAnswer" label="我的作答" width="140" show-overflow-tooltip />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.correct ? 'success' : 'danger'" size="small" effect="light">
              {{ row.correct ? '正确' : (row.type === 'ESSAY' ? '自评' : '错误') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.answeredAt) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'Stats' })

import { computed, onActivated, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import api from '../api'

use([CanvasRenderer, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const stats = ref(null)
const history = ref([])
const historyTotal = ref(0)
const historyPage = ref(1)

onMounted(() => {
  api.stats().then(s => { stats.value = s })
  loadHistory(1)
})

onActivated(() => {
  api.stats().then(s => { stats.value = s }).catch(() => {})
  loadHistory(historyPage.value)
})

async function loadHistory(p) {
  historyPage.value = p
  const res = await api.history({ page: p, size: 10 })
  history.value = res.content
  historyTotal.value = res.total
}

const cards = computed(() => {
  const o = stats.value?.overview || {}
  return [
    { label: '题目总数', value: o.totalQuestions || 0, color: 'text-gray-900' },
    { label: '累计答题', value: o.totalAnswered || 0, color: 'text-primary' },
    { label: '总体正确率', value: (o.accuracy || 0) + '%', color: 'text-emerald-500' },
    { label: '错题本', value: o.wrongCount || 0, color: 'text-red-500' }
  ]
})

const subjectRows = computed(() => stats.value?.subjectProgress || [])

const trendOption = computed(() => {
  const trend = stats.value?.dailyTrend || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['答题数', '正确数'], top: 0, right: 0 },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: trend.map(d => d.date.slice(5)), boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '答题数', type: 'line', smooth: true, data: trend.map(d => d.answered), itemStyle: { color: '#4F46E5' }, areaStyle: { color: 'rgba(79,70,229,0.08)' } },
      { name: '正确数', type: 'line', smooth: true, data: trend.map(d => d.correct), itemStyle: { color: '#10B981' } }
    ]
  }
})

const pieOption = computed(() => {
  const o = stats.value?.overview || {}
  const wrong = (o.totalAnswered || 0) - (o.totalCorrect || 0)
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['48%', '72%'], center: ['50%', '44%'],
      label: { show: true, position: 'center', formatter: () => `{v|${o.accuracy || 0}%}\n{t|正确率}`, rich: { v: { fontSize: 24, fontWeight: 'bold', color: '#1F2937' }, t: { fontSize: 12, color: '#9CA3AF', padding: [4, 0, 0, 0] } } },
      data: [
        { name: '答对', value: o.totalCorrect || 0, itemStyle: { color: '#10B981' } },
        { name: '答错', value: wrong < 0 ? 0 : wrong, itemStyle: { color: '#EF4444' } }
      ]
    }]
  }
})

function typeLabel(t) {
  return { SINGLE: '单选', MULTIPLE: '多选', JUDGE: '判断', ESSAY: '问答' }[t] || t
}

function formatTime(s) {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}
</script>
