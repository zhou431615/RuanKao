<template>
  <!-- 开始面板 -->
  <div v-if="!started" class="max-w-xl mx-auto">
    <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-7">
      <h2 class="text-lg font-semibold text-gray-900 mb-1">开始刷题</h2>
      <p class="text-sm text-gray-400 mb-6">选择范围与模式，逐题作答即时判分</p>
      <el-form label-width="80px" label-position="left">
        <el-form-item label="科目" required>
          <el-select v-model="form.subjectId" class="w-full" @change="form.chapterId = null">
            <el-option v-for="s in store.subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="form.chapterId" class="w-full" clearable placeholder="全部章节">
            <el-option v-for="c in chaptersOf(form.subjectId)" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="form.type" class="w-full" clearable placeholder="全部题型">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="问答题" value="ESSAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目来源">
          <el-radio-group v-model="form.source">
            <el-radio-button value="normal">题库</el-radio-button>
            <el-radio-button value="wrong">错题本</el-radio-button>
            <el-radio-button value="favorite">收藏夹</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="模式">
          <el-radio-group v-model="form.mode">
            <el-radio-button value="order">顺序</el-radio-button>
            <el-radio-button value="random">随机</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数量">
          <el-slider v-model="form.limit" :min="5" :max="100" :step="5" show-input class="w-full" />
        </el-form-item>
      </el-form>
      <el-button type="primary" size="large" class="!w-full mt-2" :loading="loading" @click="start">
        <Play class="w-4 h-4 mr-1.5" /> 开始练习
      </el-button>
      <p class="text-xs text-gray-400 mt-3 text-center">
        答题中可使用键盘快捷键：选项键选答案，Enter 提交，方向键翻页
      </p>
    </div>
  </div>

  <!-- 答题界面 -->
  <div v-else class="max-w-5xl mx-auto grid grid-cols-1 lg:grid-cols-[1fr_230px] gap-4 items-start">
    <div class="min-w-0 space-y-4">
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm px-5 py-3.5 flex items-center gap-4">
        <el-button circle size="small" title="结束练习（Esc）" @click="quit">
          <ArrowLeft class="w-4 h-4" />
        </el-button>
        <div class="flex-1">
          <div class="flex justify-between text-xs text-gray-400 mb-1.5">
            <span>第 {{ index + 1 }} / {{ list.length }} 题</span>
            <span class="flex items-center gap-3">
              <span>已答 {{ answeredCount }} · 对 {{ correctCount }} · 错 {{ wrongCount }}</span>
              <span class="flex items-center gap-1 text-gray-500">
                <Timer class="w-3.5 h-3.5" />{{ elapsedText }}
              </span>
            </span>
          </div>
          <el-progress :percentage="(answeredCount / list.length) * 100" :show-text="false" :stroke-width="8" color="#4F46E5" />
        </div>
      </div>

      <div v-if="current" class="bg-white rounded-2xl border border-gray-100 shadow-sm p-7">
        <div class="flex items-center gap-2 mb-4">
          <el-tag effect="dark" size="small" round :type="typeMeta(current.type).color">{{ typeMeta(current.type).label }}</el-tag>
          <span v-if="current.chapterName" class="text-xs text-gray-400">{{ current.chapterName }}</span>
          <el-tag v-if="current.difficulty" size="small" effect="plain" round>难度 {{ '★'.repeat(current.difficulty) }}</el-tag>
          <div class="flex-1"></div>
          <el-tooltip :content="current.favorite ? '取消收藏（S）' : '收藏本题（S）'">
            <button @click="toggleFavorite"
              class="p-1.5 rounded-lg transition-colors cursor-pointer"
              :class="current.favorite ? 'text-amber-400 hover:bg-amber-50' : 'text-gray-300 hover:text-amber-400 hover:bg-amber-50'">
              <Star class="w-4.5 h-4.5" :fill="current.favorite ? 'currentColor' : 'none'" />
            </button>
          </el-tooltip>
        </div>
        <RichTextViewer :text="current.stem" class="text-base leading-relaxed text-gray-900 mb-5" />

        <!-- 客观题选项 -->
        <div v-if="isChoice" class="space-y-2.5">
          <button v-for="(opt, key) in current.options" :key="key" @click="pick(key)" :disabled="revealed"
            class="w-full flex gap-3 px-4 py-3 rounded-xl text-left text-sm transition-all duration-200 border"
            :class="optionClass(key)">
            <span class="font-semibold shrink-0 w-6">{{ key }}</span>
            <RichTextViewer :text="opt" class="min-w-0 flex-1" />
            <Check v-if="revealed && isCorrectKey(key)" class="w-4 h-4 ml-auto text-emerald-500 shrink-0 self-center" />
            <X v-else-if="revealed && picked.includes(key)" class="w-4 h-4 ml-auto text-red-500 shrink-0 self-center" />
          </button>
        </div>

        <!-- 判断题 -->
        <div v-if="current.type === 'JUDGE'" class="mt-1 flex gap-3">
          <el-button :type="picked[0] === 'TRUE' ? 'success' : 'default'" :disabled="revealed" @click="pick('TRUE')" class="flex-1" size="large">正确</el-button>
          <el-button :type="picked[0] === 'FALSE' ? 'danger' : 'default'" :disabled="revealed" @click="pick('FALSE')" class="flex-1" size="large">错误</el-button>
        </div>

        <!-- 问答自评 -->
        <div v-if="current.type === 'ESSAY'" class="mt-2">
          <el-input v-model="state.essay" type="textarea" :rows="5" placeholder="写下你的作答思路（可选）" :disabled="revealed" />
          <div v-if="revealed" class="mt-4 rounded-xl bg-indigo-50/70 border border-indigo-100 p-4">
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm font-semibold text-primary">
                {{ result?.aiEvaluated ? 'AI 评分' : '自评得分' }}：{{ result?.score ?? 0 }} 分
              </span>
              <el-tag :type="result?.correct ? 'success' : 'danger'" size="small" effect="light">
                {{ result?.correct ? '掌握' : '需巩固' }}
              </el-tag>
            </div>
            <RichTextViewer v-if="result?.feedback" :text="result.feedback"
              class="text-sm text-gray-700 leading-relaxed mb-2" />
            <div class="text-xs font-semibold text-primary mb-1.5">参考答案</div>
            <RichTextViewer :text="result?.correctAnswer" class="text-sm text-gray-700 leading-relaxed" />
          </div>
          <div v-if="!revealed" class="mt-3 flex items-center gap-3">
            <span class="text-sm text-gray-500 shrink-0">自评得分：</span>
            <el-rate v-model="state.selfScore" :max="5" />
            <span v-if="state.selfScore > 0" class="text-xs text-gray-400">
              （{{ state.selfScore * 20 }} 分，{{ store.aiConfigured ? '提交后 AI 自动评分，自评仅作备用' : '≥60 记为掌握' }}）
            </span>
            <span v-else class="text-xs text-amber-500">请先自评（1-5 星），未自评无法提交</span>
          </div>
        </div>

        <!-- 判分结果 -->
        <div v-if="revealed && current.type !== 'ESSAY'" class="mt-5 rounded-xl p-4 border"
          :class="result?.correct ? 'bg-emerald-50/70 border-emerald-200' : 'bg-red-50/70 border-red-200'">
          <div class="flex items-center gap-2 mb-1.5 flex-wrap">
            <CheckCircle2 v-if="result?.correct" class="w-5 h-5 text-emerald-500" />
            <XCircle v-else class="w-5 h-5 text-red-500" />
            <span class="font-semibold" :class="result?.correct ? 'text-emerald-600' : 'text-red-600'">
              {{ result?.correct ? '回答正确' : '回答错误' }}
            </span>
            <span class="text-xs text-gray-500">你的作答：{{ prettyUserAnswer }}</span>
            <span v-if="!result?.correct" class="text-xs text-gray-500">正确答案：{{ prettyAnswer }}</span>
          </div>
          <RichTextViewer v-if="result?.analysis" :text="result.analysis"
            class="text-sm text-gray-600 leading-relaxed mt-1" />
          <div v-if="!result?.correct" class="text-xs text-gray-400 mt-2">本题已自动收入错题本，可在错题本中重练</div>
          <div v-else-if="result?.removedFromWrongBook" class="text-xs text-emerald-600 mt-2">
            本题已掌握，已自动移出错题本
          </div>
        </div>

        <div v-else-if="revealed" class="mt-5 rounded-xl p-4 border"
          :class="result?.correct ? 'bg-emerald-50/70 border-emerald-200' : 'bg-amber-50/70 border-amber-200'">
          <div class="flex items-center gap-2">
            <CheckCircle2 v-if="result?.correct" class="w-5 h-5 text-emerald-500" />
            <Lightbulb v-else class="w-5 h-5 text-amber-500" />
            <span class="font-semibold" :class="result?.correct ? 'text-emerald-600' : 'text-amber-600'">
              {{ result?.correct
                ? `已掌握（${result?.aiEvaluated ? 'AI' : '自评'} ${result?.score ?? 0} 分）`
                : `仍需巩固（${result?.aiEvaluated ? 'AI' : '自评'} ${result?.score ?? 0} 分），建议对照参考答案再复习` }}
            </span>
          </div>
          <div v-if="!result?.correct" class="text-xs text-gray-400 mt-2">
            本题已自动收入错题本，可在错题本中重练
          </div>
          <div v-else-if="result?.removedFromWrongBook" class="text-xs text-emerald-600 mt-2">
            本题已掌握，已自动移出错题本
          </div>
        </div>

        <div class="mt-6 flex items-center justify-between">
          <el-button :disabled="index === 0" @click="prev">
            <ChevronLeft class="w-4 h-4 mr-1" /> 上一题
          </el-button>
          <div class="flex items-center gap-2">
            <el-button v-if="revealed" text @click="redo">
              <RotateCcw class="w-4 h-4 mr-1" /> 重做本题
            </el-button>
            <el-button v-if="!revealed" type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">
              提交答案
            </el-button>
            <el-button v-else type="primary" @click="next">
              {{ index === list.length - 1 ? '完成练习' : '下一题' }} <ChevronRight class="w-4 h-4 ml-1" />
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 答题卡 -->
    <aside class="lg:sticky lg:top-4 space-y-4">
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-4">
        <div class="flex items-center justify-between mb-3">
          <h3 class="font-semibold text-gray-800 text-sm">答题卡</h3>
          <span class="text-xs text-gray-400">{{ answeredCount }}/{{ list.length }}</span>
        </div>
        <div class="grid grid-cols-5 gap-1.5 max-h-64 overflow-y-auto pr-1">
          <button v-for="(q, i) in list" :key="q.id" @click="goTo(i)" :title="'第 ' + (i + 1) + ' 题'"
            class="h-8 rounded-lg text-xs font-medium border transition-all"
            :class="cardClass(q, i)">{{ i + 1 }}</button>
        </div>
        <div class="mt-3 space-y-1.5 text-xs text-gray-500">
          <div class="flex items-center gap-2"><span class="w-3 h-3 rounded bg-emerald-100 border border-emerald-300"></span>答对</div>
          <div class="flex items-center gap-2"><span class="w-3 h-3 rounded bg-red-100 border border-red-300"></span>答错</div>
          <div class="flex items-center gap-2"><span class="w-3 h-3 rounded bg-indigo-100 border border-indigo-300"></span>已选未提交</div>
          <div class="flex items-center gap-2"><span class="w-3 h-3 rounded bg-gray-100 border border-gray-300"></span>未作答</div>
        </div>
      </div>

      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-4">
        <h3 class="font-semibold text-gray-800 text-sm mb-3">快捷键</h3>
        <div class="space-y-1.5">
          <div v-for="s in shortcuts" :key="s.key" class="flex items-center justify-between text-xs">
            <span class="text-gray-500">{{ s.desc }}</span>
            <kbd class="px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 font-mono border border-gray-200">{{ s.key }}</kbd>
          </div>
        </div>
      </div>
    </aside>
  </div>

  <!-- 完成弹窗 -->
  <el-dialog v-model="finished" title="练习完成" width="400px" align-center :close-on-click-modal="false">
    <div class="text-center py-2">
      <div class="text-4xl font-bold text-primary mb-2">{{ accuracy }}%</div>
      <div class="text-sm text-gray-500 mb-4">
        共 {{ list.length }} 题 · 已完成 {{ answeredCount }} 题 · 答对 {{ correctCount }} 题 · 答错 {{ wrongCount }} 题 · 未答 {{ skippedCount }} 题 · 用时 {{ elapsedText }}
      </div>
      <el-progress type="dashboard" :percentage="accuracy"
        :color="accuracy >= 80 ? '#10B981' : accuracy >= 60 ? '#F59E0B' : '#EF4444'" :width="120" />
      <div class="mt-3 text-xs text-gray-400">
        {{ completionMessage }}
      </div>
    </div>
    <template #footer>
      <el-button @click="retryWrong" :disabled="wrongCount === 0">
        <RotateCcw class="w-4 h-4 mr-1" /> 重练错题（{{ wrongCount }}）
      </el-button>
      <el-button type="primary" @click="resetSession">返回</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineOptions({ name: 'Practice' })

import { computed, onActivated, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Play, ArrowLeft, Check, X, CheckCircle2, XCircle, Star,
  Timer, RotateCcw, ChevronLeft, ChevronRight, Lightbulb
} from '@lucide/vue'
import api from '../api'
import { useAppStore } from '../stores/app'
import { useRoute } from 'vue-router'
import { notify, confirmAction } from '../utils/feedback'
import RichTextViewer from '../components/RichTextViewer.vue'

const store = useAppStore()
const route = useRoute()

const started = ref(false)
const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const index = ref(0)
const finished = ref(false)
const elapsed = ref(0)
let timerId = null

/** 每题作答状态（key = questionId），来回切题时保留已答内容与判分结果 */
const answers = reactive(new Map())

const form = reactive({ subjectId: null, chapterId: null, type: null, source: 'normal', mode: 'order', limit: 20 })

const current = computed(() => list.value[index.value] || null)
const state = computed(() => (current.value ? answers.get(current.value.id) || null : null))
const picked = computed(() => state.value?.picked || [])
const result = computed(() => state.value?.result || null)
const revealed = computed(() => !!state.value?.submitted)
const isChoice = computed(() => current.value?.type === 'SINGLE' || current.value?.type === 'MULTIPLE')

const answeredCount = computed(() => list.value.filter(q => answers.get(q.id)?.submitted).length)
const wrongCount = computed(() => list.value.filter(q => {
  const s = answers.get(q.id)
  return s?.submitted && !s.result?.correct
}).length)
const correctCount = computed(() => answeredCount.value - wrongCount.value)
const accuracy = computed(() => (answeredCount.value ? Math.round((correctCount.value / answeredCount.value) * 100) : 0))
const skippedCount = computed(() => Math.max(0, list.value.length - answeredCount.value))
const completionMessage = computed(() => {
  if (skippedCount.value > 0) return `还有 ${skippedCount.value} 题未作答，建议完成后再看掌握度。`
  if (accuracy.value >= 80) return '掌握得不错，继续保持！'
  if (accuracy.value >= 60) return '基本掌握，建议重点复习错题。'
  return '还需加强，先把错题吃透吧。'
})

const canSubmit = computed(() => {
  if (!state.value || revealed.value) return false
  // 问答题必须先自评：避免默认分值被误判为「已掌握」而漏进错题本
  if (current.value?.type === 'ESSAY') return state.value.selfScore > 0
  return picked.value.length > 0
})

const prettyAnswer = computed(() => {
  if (!result.value) return ''
  const a = result.value.correctAnswer
  return current.value?.type === 'JUDGE' ? (a === 'TRUE' ? '正确' : '错误') : a
})
const prettyUserAnswer = computed(() => {
  const raw = state.value?.userAnswer || ''
  return current.value?.type === 'JUDGE' ? (raw === 'TRUE' ? '正确' : raw === 'FALSE' ? '错误' : '未作答') : raw
})

const elapsedText = computed(() => {
  const m = Math.floor(elapsed.value / 60)
  const s = elapsed.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const shortcuts = computed(() => {
  const base = [
    { key: 'Enter', desc: '提交 / 下一题' },
    { key: '←  →', desc: '上一题 / 下一题' },
    { key: 'S', desc: '收藏本题' },
    { key: 'Esc', desc: '结束练习' }
  ]
  if (current.value?.type === 'JUDGE') {
    return [{ key: '1 / T', desc: '正确' }, { key: '2 / F', desc: '错误' }, ...base]
  }
  if (isChoice.value) return [{ key: 'A–F / 1–6', desc: '选择选项' }, ...base]
  return base
})

function chaptersOf(subjectId) {
  return store.subjects.find(s => s.id === subjectId)?.chapters || []
}

function typeMeta(t) {
  return {
    SINGLE: { label: '单选', color: 'primary' },
    MULTIPLE: { label: '多选', color: 'warning' },
    JUDGE: { label: '判断', color: 'success' },
    ESSAY: { label: '问答', color: 'info' }
  }[t] || { label: t, color: 'info' }
}

/** 为当前题单重置作答状态 */
function initStates() {
  answers.clear()
  list.value.forEach(q => answers.set(q.id, {
    picked: [], essay: '', selfScore: 0, submitted: false, result: null, userAnswer: ''
  }))
}

function startTimer() {
  stopTimer()
  elapsed.value = 0
  const startAt = Date.now()
  timerId = setInterval(() => { elapsed.value = Math.floor((Date.now() - startAt) / 1000) }, 1000)
}
function stopTimer() {
  if (timerId) { clearInterval(timerId); timerId = null }
}

onMounted(async () => {
  await store.loadSubjects().catch(() => {})
  const query = route.query
  if (query.source === 'wrong' || query.source === 'favorite') form.source = query.source
  let subjectId = query.subjectId ? Number(query.subjectId) : 0
  if (!subjectId && store.subjects.length) subjectId = store.subjects[0].id
  if (subjectId) form.subjectId = subjectId
  if (query.chapterId) form.chapterId = Number(query.chapterId)
  window.addEventListener('keydown', onKeydown)
})

onActivated(async () => {
  await store.loadSubjects().catch(() => {})
  if (!started.value && form.subjectId && !store.subjects.some(s => s.id === form.subjectId)) {
    form.subjectId = store.subjects[0]?.id || null
    form.chapterId = null
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  stopTimer()
})

async function start() {
  if (!form.subjectId) return ElMessage.warning('请选择科目')
  loading.value = true
  try {
    const res = await api.fetchPractice({
      subjectId: form.subjectId, chapterId: form.chapterId || undefined,
      type: form.type || undefined, source: form.source, mode: form.mode, limit: form.limit
    })
    if (!res.length) {
      const sourceName = { wrong: '错题本', favorite: '收藏夹' }[form.source] || '该范围'
      ElMessage.warning(`${sourceName}里还没有题目，换个范围试试`)
      return
    }
    list.value = res
    index.value = 0
    initStates()
    started.value = true
    startTimer()
  } finally {
    loading.value = false
  }
}

function pick(key) {
  if (revealed.value || !state.value) return
  const type = current.value.type
  if (type === 'SINGLE' || type === 'JUDGE') state.value.picked = [key]
  else if (type === 'MULTIPLE') {
    const i = state.value.picked.indexOf(key)
    if (i >= 0) state.value.picked.splice(i, 1)
    else state.value.picked.push(key)
  }
}

function optionClass(key) {
  const selected = picked.value.includes(key)
  if (revealed.value) {
    if (isCorrectKey(key)) return 'bg-emerald-50 border-emerald-300 text-emerald-700'
    if (selected) return 'bg-red-50 border-red-300 text-red-600'
    return 'bg-gray-50 border-transparent text-gray-500'
  }
  return selected
    ? 'bg-indigo-50 border-primary text-primary shadow-sm shadow-indigo-100 scale-[1.01]'
    : 'bg-gray-50 border-transparent text-gray-700 hover:bg-indigo-50/60 hover:border-indigo-200 cursor-pointer'
}

function isCorrectKey(key) {
  return !!result.value?.correctAnswer?.includes(key)
}

function cardClass(q, i) {
  const s = answers.get(q.id)
  const isCurrent = i === index.value
  if (s?.submitted) {
    const ok = s.result?.correct
    return isCurrent
      ? (ok ? 'bg-emerald-500 text-white border-emerald-500 ring-2 ring-emerald-200'
            : 'bg-red-500 text-white border-red-500 ring-2 ring-red-200')
      : (ok ? 'bg-emerald-50 text-emerald-600 border-emerald-200 hover:bg-emerald-100'
            : 'bg-red-50 text-red-600 border-red-200 hover:bg-red-100')
  }
  if (isCurrent) return 'bg-primary text-white border-primary ring-2 ring-indigo-200'
  if (s?.picked?.length) return 'bg-indigo-50 text-indigo-600 border-indigo-200 hover:bg-indigo-100'
  return 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
}

async function submit() {
  if (!canSubmit.value) return
  const q = current.value
  const st = state.value
  const isEssay = q.type === 'ESSAY'
  let userAnswer
  if (isEssay) userAnswer = st.essay?.trim() || '（未作答）'
  else if (q.type === 'JUDGE') userAnswer = st.picked[0]
  else if (q.type === 'MULTIPLE') userAnswer = [...st.picked].sort().join('')
  else userAnswer = st.picked[0]

  submitting.value = true
  try {
    const res = await api.submitAnswer({
      questionId: q.id, userAnswer,
      selfScore: isEssay ? st.selfScore * 20 : undefined
    })
    st.result = res
    st.submitted = true
    st.userAnswer = userAnswer
  } finally {
    submitting.value = false
  }
}

function redo() {
  const st = state.value
  if (!st) return
  st.picked = []
  st.essay = ''
  st.selfScore = 0
  st.submitted = false
  st.result = null
  st.userAnswer = ''
}

function goTo(i) {
  if (i < 0 || i >= list.value.length) return
  index.value = i
}

function next() {
  if (index.value === list.value.length - 1) { stopTimer(); finished.value = true; return }
  goTo(index.value + 1)
}

function prev() {
  goTo(index.value - 1)
}

async function toggleFavorite() {
  const q = current.value
  if (!q) return
  try {
    if (q.favorite) {
      await api.removeFavorite(q.id)
      q.favorite = false
      notify.info('已取消收藏')
    } else {
      await api.addFavorite(q.id)
      q.favorite = true
      notify.success('已收藏，可在收藏夹中查看')
    }
  } catch {
    // 拦截器已给出提示
  }
}

async function quit() {
  if (answeredCount.value > 0) {
    try {
      await confirmAction(`本次已作答 ${answeredCount.value} 题，确定结束练习吗？`, { title: '结束练习' })
    } catch {
      return
    }
  }
  resetSession()
}

function resetSession() {
  stopTimer()
  started.value = false
  finished.value = false
  list.value = []
  index.value = 0
  answers.clear()
  store.loadSubjects(true).catch(() => {})
}

function retryWrong() {
  const wrongIds = new Set(list.value.filter(q => {
    const s = answers.get(q.id)
    return s?.submitted && !s.result?.correct
  }).map(q => q.id))
  list.value = list.value.filter(q => wrongIds.has(q.id))
  index.value = 0
  finished.value = false
  initStates()
  startTimer()
}

/** 键盘快捷键：输入框内不拦截，避免影响作答输入 */
function onKeydown(e) {
  if (!started.value || finished.value || !current.value) return
  const t = e.target
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return

  const key = e.key
  const lower = typeof key === 'string' ? key.toLowerCase() : ''

  if (isChoice.value) {
    const keys = Object.keys(current.value.options || {})
    const byDigit = /^[1-6]$/.test(key) ? Number(key) - 1 : -1
    const byLetter = /^[a-f]$/.test(lower) ? lower.charCodeAt(0) - 97 : -1
    const idx = byDigit >= 0 ? byDigit : byLetter
    if (idx >= 0 && idx < keys.length) { e.preventDefault(); pick(keys[idx]); return }
  }

  if (current.value.type === 'JUDGE') {
    if (key === '1' || lower === 't') { e.preventDefault(); pick('TRUE'); return }
    if (key === '2' || lower === 'f') { e.preventDefault(); pick('FALSE'); return }
  }

  if (key === 'Enter') { e.preventDefault(); revealed.value ? next() : submit(); return }
  if (key === 'ArrowRight') { e.preventDefault(); next(); return }
  if (key === 'ArrowLeft') { e.preventDefault(); prev(); return }
  if (lower === 's') { e.preventDefault(); toggleFavorite(); return }
  if (key === 'Escape') { e.preventDefault(); quit() }
}
</script>
