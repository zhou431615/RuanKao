<template>
  <div class="min-h-screen flex items-center justify-center px-4"
    style="background: radial-gradient(1200px 600px at 20% -10%, #EEF2FF 0%, #F5F7FA 55%)">
    <div class="w-full max-w-sm">
      <div class="flex flex-col items-center mb-7">
        <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-primary to-primary-light flex items-center justify-center shadow-lg shadow-indigo-200 mb-3">
          <BookOpen class="w-6 h-6 text-white" />
        </div>
        <h1 class="text-xl font-semibold text-gray-900">软考刷题</h1>
        <p class="text-xs text-gray-400 mt-1">登录后开始练习与管理题库</p>
      </div>

      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-7">
        <el-form label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username"
              :prefix-icon="UserIcon" @input="clearError" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" size="large" type="password" show-password
              placeholder="请输入密码" autocomplete="current-password"
              :prefix-icon="Lock" @input="clearError" />
            <div v-if="errorTip" class="text-xs text-red-500 mt-1">{{ errorTip }}</div>
          </el-form-item>
        </el-form>

        <el-button type="primary" size="large" class="!w-full" :loading="loading" :disabled="!canSubmit"
          @click="submit">
          {{ loading ? '登录中…' : '登录' }}
        </el-button>

        <el-alert type="info" :closable="false" class="mt-5" show-icon>
          <template #title>
            <span class="text-xs leading-relaxed">
              首次启动已自动创建默认账号 <b>admin</b> / <b>admin123</b>。登录成功后可在右上角菜单中修改密码。
            </span>
          </template>
        </el-alert>
      </div>

      <p class="text-center text-xs text-gray-400 mt-5">数据保存在本地 H2 数据库，重启不丢失</p>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpen, User as UserIcon, Lock } from '@lucide/vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'
import { notify } from '../utils/feedback'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorTip = ref('')

const canSubmit = computed(() => form.username.trim() && form.password)

function clearError() {
  errorTip.value = ''
}

async function submit() {
  if (!canSubmit.value || loading.value) return
  clearError()
  loading.value = true
  try {
    const user = await auth.login({ username: form.username.trim(), password: form.password })
    notify.success(`欢迎回来，${user.displayName || user.username}`)
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/dashboard')
  } catch (e) {
    errorTip.value = e?.response?.data?.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}

// 已登录时直接放行，避免重复登录
auth.ensureLoaded().then(ok => {
  if (ok) router.replace('/dashboard')
})
</script>
