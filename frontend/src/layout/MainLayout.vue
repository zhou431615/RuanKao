<template>
  <div class="flex h-full min-h-screen">
    <aside class="fixed inset-y-0 left-0 z-30 flex w-56 flex-col bg-white border-r border-gray-100 shadow-sm">
      <div class="flex items-center gap-2.5 px-5 h-16 border-b border-gray-50">
        <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-primary-light flex items-center justify-center shadow-md shadow-indigo-200">
          <BookOpen class="w-5 h-5 text-white" />
        </div>
        <div>
          <div class="text-base font-semibold text-gray-900 leading-tight">软考刷题</div>
          <div class="text-[11px] text-gray-400">Exam Practice</div>
        </div>
      </div>
      <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <router-link v-for="item in menus" :key="item.path" :to="item.path"
          class="group flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer"
          :class="isActive(item.path)
            ? 'bg-primary text-white shadow-md shadow-indigo-200'
            : 'text-gray-600 hover:bg-indigo-50 hover:text-primary'">
          <component :is="item.icon" class="w-[18px] h-[18px]" :class="isActive(item.path) ? 'text-white' : 'text-gray-400 group-hover:text-primary'" />
          {{ item.title }}
        </router-link>
      </nav>
      <div class="px-4 py-3 border-t border-gray-50 text-[11px] text-gray-400 leading-relaxed">
        数据存储于本地 H2 数据库<br />重启不丢失
      </div>
    </aside>
    <div class="flex-1 ml-56 flex flex-col min-w-0">
      <header class="fixed top-0 right-0 left-56 z-20 h-16 bg-white/80 backdrop-blur border-b border-gray-100 flex items-center px-6 justify-between">
        <div class="flex items-center gap-2 text-sm text-gray-500">
          <component :is="currentIcon" class="w-4 h-4 text-primary" />
          <span class="font-medium text-gray-800">{{ currentTitle }}</span>
        </div>
        <div class="flex items-center gap-3">
          <el-button size="small" text @click="openAiSettings">
            <Settings class="w-4 h-4 mr-1" /> AI 设置
          </el-button>
          <el-tag v-if="!store.aiConfigured" type="warning" effect="plain" size="small">AI 未配置</el-tag>
          <el-tag v-else type="success" effect="plain" size="small">AI 已启用</el-tag>
          <el-dropdown trigger="click" @command="onUserCommand">
            <button class="flex items-center gap-2 pl-1 pr-2 py-1 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer">
              <span class="w-7 h-7 rounded-full bg-indigo-50 text-primary flex items-center justify-center">
                <User class="w-4 h-4" />
              </span>
              <span class="text-sm text-gray-700 max-w-24 truncate">{{ displayName }}</span>
              <ChevronDown class="w-3.5 h-3.5 text-gray-400" />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">
                  <KeyRound class="w-3.5 h-3.5 mr-1.5" /> 修改密码
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <LogOut class="w-3.5 h-3.5 mr-1.5" /> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="flex-1 pt-16 px-6 py-6 max-w-[1400px] w-full mx-auto">
        <router-view v-slot="{ Component }">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </router-view>
      </main>
    </div>
    <AiSettingsDialog ref="aiSettings" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'
import {
  BookOpen, LayoutDashboard, Database, PencilLine, AlertCircle, Star, BarChart3,
  Settings, User, ChevronDown, LogOut, KeyRound
} from '@lucide/vue'
import AiSettingsDialog from '../components/AiSettingsDialog.vue'
import api from '../api'
import { notify, confirmAction, withLoading } from '../utils/feedback'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const auth = useAuthStore()
const aiSettings = ref(null)
const changingPassword = ref(false)

const menus = [
  { path: '/dashboard', title: '仪表盘', icon: LayoutDashboard },
  { path: '/bank', title: '题库管理', icon: Database },
  { path: '/practice', title: '刷题练习', icon: PencilLine },
  { path: '/wrong', title: '错题本', icon: AlertCircle },
  { path: '/favorites', title: '收藏夹', icon: Star },
  { path: '/stats', title: '学习统计', icon: BarChart3 }
]

const currentTitle = computed(() => route.meta.title || '')
const currentIcon = computed(() => menus.find(m => m.path === route.path)?.icon || BookOpen)
const isActive = (path) => route.path === path
const cachedViews = ['Dashboard', 'QuestionBank', 'Practice', 'WrongBook', 'Favorites', 'Stats']

const displayName = computed(() => auth.user?.displayName || auth.user?.username || '未登录')

function openAiSettings() {
  aiSettings.value?.open()
}

async function onUserCommand(cmd) {
  if (cmd === 'password') await openChangePassword()
  else if (cmd === 'logout') await doLogout()
}

async function openChangePassword() {
  let payload
  try {
    payload = await ElMessageBox.prompt(
      '新密码长度需为 6-64 位，修改后下次登录生效。',
      '修改密码',
      {
        inputType: 'password',
        inputPlaceholder: '请输入新密码',
        inputValidator: v => (v && v.length >= 6 && v.length <= 64 ? true : '新密码长度需为 6-64 位'),
        confirmButtonText: '确认修改',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  const newPassword = payload.value
  let oldPassword
  try {
    const confirmOld = await ElMessageBox.prompt('请输入当前密码以确认身份', '修改密码', {
      inputType: 'password',
      inputPlaceholder: '当前密码',
      inputValidator: v => (v ? true : '请输入当前密码'),
      confirmButtonText: '确认修改',
      cancelButtonText: '取消'
    })
    oldPassword = confirmOld.value
  } catch {
    return
  }
  await withLoading(changingPassword,
    () => api.changePassword({ oldPassword, newPassword, confirmPassword: newPassword }),
    { success: '密码已修改，下次登录请使用新密码' })
}

async function doLogout() {
  try {
    await confirmAction('确定退出登录吗？', { title: '退出登录', danger: false })
  } catch {
    return
  }
  await auth.logout()
  notify.success('已退出登录')
  router.replace('/login')
}

store.loadAiStatus().catch(() => {})
auth.ensureLoaded().catch(() => {})
</script>
