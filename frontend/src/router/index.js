import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录', public: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'bank', name: 'QuestionBank', component: () => import('../views/QuestionBank.vue'), meta: { title: '题库管理' } },
      { path: 'practice', name: 'Practice', component: () => import('../views/Practice.vue'), meta: { title: '刷题练习' } },
      { path: 'wrong', name: 'WrongBook', component: () => import('../views/WrongBook.vue'), meta: { title: '错题本' } },
      { path: 'favorites', name: 'Favorites', component: () => import('../views/Favorites.vue'), meta: { title: '收藏夹' } },
      { path: 'stats', name: 'Stats', component: () => import('../views/Stats.vue'), meta: { title: '学习统计' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true

  const auth = useAuthStore()
  // 首次进入需向后端确认登录态（刷新页面后内存状态会丢失）
  const ok = await auth.ensureLoaded()
  if (ok) return true

  return { path: '/login', query: { redirect: to.fullPath } }
})

router.afterEach((to) => {
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + '软考刷题'
})

export default router
