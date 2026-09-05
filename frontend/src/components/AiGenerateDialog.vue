<template>
  <el-dialog v-model="visible" title="AI 智能出题" width="720px" :close-on-click-modal="false">
    <template #header>
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-xl bg-gradient-to-br from-primary to-primary-light flex items-center justify-center">
          <Sparkles class="w-4 h-4 text-white" />
        </div>
        <div>
          <div class="font-semibold text-gray-900">AI 智能出题</div>
          <div class="text-xs text-gray-400">调用大模型按考点生成题目，预览确认后入库</div>
        </div>
      </div>
    </template>

    <el-alert v-if="!store.aiConfigured" type="warning" :closable="false" class="mb-4"
      title="AI 功能未启用"
      description="未检测到 API Key，点击下方按钮可在页面中直接完成配置（支持 DeepSeek 等 OpenAI 兼容服务）。" />
    <div v-if="!store.aiConfigured" class="-mt-3 mb-4">
      <el-button type="primary" plain size="small" @click="openConfig">在页面中配置 AI</el-button>
    </div>

    <el-form v-else label-width="90px" label-position="left">
      <div class="grid grid-cols-2 gap-x-4">
        <el-form-item label="科目" required>
          <el-select v-model="form.subjectId" placeholder="选择科目" class="w-full" @change="form.chapterId = null">
            <el-option v-for="s in store.subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="form.chapterId" placeholder="可选" class="w-full" clearable>
            <el-option v-for="c in chaptersOf(form.subjectId)" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型" required>
          <el-select v-model="form.type" class="w-full">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="问答题" value="ESSAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="form.count" :min="1" :max="20" class="!w-full" />
        </el-form-item>
      </div>
      <el-form-item label="考点" required>
        <el-input v-model="form.topic" placeholder="如：二叉树遍历、死锁、软件维护分类" />
      </el-form-item>
      <el-form-item label="补充要求">
        <el-input v-model="form.extraRequirement" type="textarea" :rows="2"
          placeholder="可选，如：难度中等、贴近近年真题风格" />
      </el-form-item>
    </el-form>

    <div v-if="generated.length" class="mt-2">
      <div class="flex items-center justify-between mb-2">
        <span class="text-sm font-medium text-gray-700">生成预览（{{ generated.length }} 题）</span>
        <span class="text-xs text-gray-400">确认无误后点击下方"确认入库"</span>
      </div>
      <div class="max-h-72 overflow-y-auto space-y-3 pr-1">
        <div v-for="(q, i) in generated" :key="i" class="border border-gray-100 rounded-xl p-3.5 bg-gray-50/60">
          <div class="flex items-center gap-2 mb-1.5">
            <el-tag size="small" round effect="dark" :type="typeColor(q.type)">{{ typeLabel(q.type) }}</el-tag>
            <span class="text-xs text-gray-400">难度 {{ q.difficulty || 3 }}</span>
          </div>
          <RichTextViewer :text="q.stem" class="text-sm text-gray-800 leading-relaxed" />
          <div v-if="q.options && q.options.length" class="mt-1.5 text-xs text-gray-500 space-y-0.5">
            <div v-for="opt in q.options" :key="opt.key" class="flex gap-1">
              <span>{{ opt.key }}.</span>
              <RichTextViewer :text="opt.content" class="min-w-0 flex-1" />
            </div>
          </div>
          <div class="mt-1.5 text-xs flex gap-1"><span class="text-emerald-600 font-medium shrink-0">答案：</span><RichTextViewer :text="q.answer" class="text-gray-600 min-w-0" /></div>
          <div class="mt-0.5 text-xs text-gray-400 line-clamp-2">解析：<RichTextViewer :text="q.analysis" class="inline" /></div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="openConfig">
        <Settings class="w-4 h-4 mr-1" /> AI 设置
      </el-button>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="generated.length" type="success" :loading="confirming" @click="confirmImport">
        确认入库
      </el-button>
      <el-button v-else type="primary" :loading="generating" :disabled="!store.aiConfigured" @click="generate">
        <Sparkles class="w-4 h-4 mr-1" /> 生成题目
      </el-button>
    </template>
  </el-dialog>

  <AiSettingsDialog ref="aiSettings" @updated="onAiUpdated" />
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Sparkles, Settings } from '@lucide/vue'
import api from '../api'
import { useAppStore } from '../stores/app'
import AiSettingsDialog from './AiSettingsDialog.vue'
import RichTextViewer from './RichTextViewer.vue'

const visible = ref(false)
const generating = ref(false)
const confirming = ref(false)
const generated = ref([])
const aiSettings = ref(null)
const store = useAppStore()

const form = reactive({ subjectId: null, chapterId: null, type: 'SINGLE', count: 5, topic: '', extraRequirement: '' })
const emit = defineEmits(['imported'])

function open() {
  generated.value = []
  if (!form.subjectId && store.subjects.length) {
    form.subjectId = store.subjects[0].id
  }
  visible.value = true
}

function openConfig() {
  aiSettings.value?.open()
}

function onAiUpdated(status) {
  store.aiConfigured = status.configured
  store.aiStatus = status
}

defineExpose({ open })

function chaptersOf(subjectId) {
  return store.subjects.find(s => s.id === subjectId)?.chapters || []
}

async function generate() {
  if (!form.subjectId) return ElMessage.warning('请选择科目')
  if (!form.topic.trim()) return ElMessage.warning('请填写考点')
  generating.value = true
  try {
    const res = await api.aiGenerate({ ...form, count: form.count })
    generated.value = res.questions || []
    if (!generated.value.length) ElMessage.warning('AI 未生成题目，请调整考点后重试')
  } finally {
    generating.value = false
  }
}

async function confirmImport() {
  confirming.value = true
  try {
    const res = await api.aiConfirm({
      subjectId: form.subjectId,
      chapterId: form.chapterId,
      questions: generated.value,
      source: 'AI 生成'
    })
    ElMessage.success(`成功入库 ${res.successCount} 题`)
    visible.value = false
    emit('imported')
  } finally {
    confirming.value = false
  }
}

function typeLabel(t) {
  return { SINGLE: '单选', MULTIPLE: '多选', JUDGE: '判断', ESSAY: '问答' }[t] || t
}
function typeColor(t) {
  return { SINGLE: 'primary', MULTIPLE: 'warning', JUDGE: 'success', ESSAY: 'info' }[t] || 'info'
}
</script>
