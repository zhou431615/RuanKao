<template>
  <el-dialog v-model="visible" title="AI 配置" width="560px" :close-on-click-modal="false">
    <el-alert type="info" :closable="false" class="mb-4"
      title="保存后立即生效并持久化"
      description="支持 DeepSeek 等 OpenAI 兼容服务；本地 Ollama 等服务可不填 Key。" />
    <el-form label-width="110px" label-position="left">
      <el-form-item label="API 地址" required>
        <el-input v-model="configForm.baseUrl" placeholder="如：https://api.deepseek.com" />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="configForm.apiKey" type="password" show-password
          placeholder="云服务填写 Key；本地服务可留空" />
      </el-form-item>
      <el-form-item label="模型名称" required>
        <el-input v-model="configForm.model" placeholder="如：deepseek-chat" />
      </el-form-item>
      <el-form-item label="采样温度">
        <el-input-number v-model="configForm.temperature" :min="0" :max="2" :step="0.1" />
      </el-form-item>
      <el-form-item label="超时时间(秒)">
        <el-input-number v-model="configForm.timeoutSeconds" :min="30" :max="600" :step="30" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="testingConfig" @click="testConfig">测试连接</el-button>
      <el-button type="primary" :loading="savingConfig" @click="saveConfig">保存配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineOptions({ name: 'AiSettingsDialog' })

import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useAppStore } from '../stores/app'

const visible = ref(false)
const savingConfig = ref(false)
const testingConfig = ref(false)
const store = useAppStore()
const emit = defineEmits(['updated'])

const configForm = reactive({
  baseUrl: 'https://api.deepseek.com',
  apiKey: '',
  model: 'deepseek-chat',
  temperature: 1,
  timeoutSeconds: 180
})

async function open() {
  try {
    const status = await store.loadAiStatus()
    if (status) {
      configForm.baseUrl = status.baseUrl || 'https://api.deepseek.com'
      configForm.model = status.model || 'deepseek-chat'
      configForm.temperature = status.temperature ?? 1
      configForm.timeoutSeconds = status.timeoutSeconds || 180
    }
  } catch {
    // 状态接口失败时保留默认值
  }
  configForm.apiKey = ''
  visible.value = true
}

async function saveConfig() {
  if (!configForm.baseUrl.trim()) return ElMessage.warning('请填写 API 地址')
  if (!configForm.model.trim()) return ElMessage.warning('请填写模型名称')
  savingConfig.value = true
  try {
    const status = await api.updateAiConfig({
      baseUrl: configForm.baseUrl.trim(),
      apiKey: configForm.apiKey.trim(),
      model: configForm.model.trim(),
      temperature: configForm.temperature,
      timeoutSeconds: configForm.timeoutSeconds,
      clearApiKey: false
    })
    store.aiConfigured = status.configured
    store.aiStatus = status
    ElMessage.success(status.configured ? 'AI 配置已保存并启用' : 'AI 配置已保存（未填写 API Key）')
    configForm.apiKey = ''
    visible.value = false
    emit('updated', status)
  } finally {
    savingConfig.value = false
  }
}

async function testConfig() {
  testingConfig.value = true
  try {
    const res = await api.testAiConnection()
    ElMessage.success(`连接成功，模型「${res.model}」回复：${res.reply}`)
  } finally {
    testingConfig.value = false
  }
}

defineExpose({ open })
</script>
