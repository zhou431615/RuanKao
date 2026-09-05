<template>
  <el-dialog :model-value="modelValue" @update:model-value="v => $emit('update:modelValue', v)" title="导入题库" width="560px">
    <el-alert type="info" :closable="false" class="mb-4" title="支持格式"
      description="Excel/JSON 直接按文件内容导入；PDF/Word 提取文本后由 AI 解析。建议先下载 Excel 模板查看列格式。" />
    <div class="grid grid-cols-2 gap-x-3 mb-4">
      <el-form-item label="目标科目">
        <el-input v-model="target.subject" placeholder="PDF/Word 可选，如：软件设计师" />
      </el-form-item>
      <el-form-item label="目标章节">
        <el-input v-model="target.chapter" placeholder="PDF/Word 可选" />
      </el-form-item>
    </div>
    <el-upload drag :action="api.importUrl" :data="uploadData" :show-file-list="true"
      accept=".xlsx,.json,.pdf,.doc,.docx" :on-success="onSuccess" :on-error="onError" :auto-upload="true">
      <div class="py-4">
        <UploadCloud class="w-10 h-10 mx-auto text-gray-300 mb-2" />
        <div class="text-sm text-gray-600">拖拽文件到此处，或 <em class="text-primary not-italic">点击上传</em></div>
        <div class="text-xs text-gray-400 mt-1">支持 .xlsx / .json / .pdf / .doc / .docx，最大 20MB</div>
      </div>
    </el-upload>
    <div class="flex items-center justify-between mt-3 text-sm">
      <el-button text type="primary" @click="downloadTemplate">
        <FileDown class="w-4 h-4 mr-1" /> 下载 Excel 模板
      </el-button>
    </div>
    <div v-if="result" class="mt-4 border border-gray-100 rounded-xl p-3.5 bg-gray-50/60">
      <div class="flex items-center gap-3 mb-2">
        <el-tag :type="result.failCount ? 'warning' : 'success'">
          成功 {{ result.successCount }} / 共 {{ result.total }}
        </el-tag>
        <span class="text-xs text-gray-400">{{ result.fileName }}</span>
      </div>
      <div v-if="result.failCount" class="max-h-40 overflow-y-auto space-y-1">
        <div v-for="(d, i) in result.details.filter(x => !x.success)" :key="i" class="text-xs text-red-500">
          第 {{ d.row }} 条失败：{{ d.message }}
        </div>
      </div>
    </div>
    <template #footer>
      <el-button type="primary" @click="$emit('update:modelValue', false)">完成</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadCloud, FileDown } from '@lucide/vue'
import api from '../api'
import { useAppStore } from '../stores/app'
import { downloadFile } from '../utils/download'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'imported'])
const store = useAppStore()
const result = ref(null)
const target = reactive({ subject: '', chapter: '' })

const uploadData = computed(() => {
  const data = {}
  if (target.subject.trim()) data.subject = target.subject.trim()
  if (target.chapter.trim()) data.chapter = target.chapter.trim()
  return data
})

function onSuccess(res) {
  if (res.success) {
    result.value = res.data
    ElMessage.success(`导入完成：成功 ${res.data.successCount} 题`)
    store.invalidate()
    store.loadSubjects(true).catch(() => {})
    emit('imported')
    target.subject = ''
    target.chapter = ''
  } else {
    ElMessage.error(res.message || '导入失败')
  }
}

function onError(err) {
  ElMessage.error(err.message || '上传失败')
}

async function downloadTemplate() {
  try {
    await downloadFile(api.templateUrl, '题库导入模板.xlsx')
    ElMessage.success('模板已开始下载')
  } catch (e) {
    ElMessage.error(e.message || '模板下载失败')
  }
}
</script>
