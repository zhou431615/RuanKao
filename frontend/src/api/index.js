import axios from 'axios'
import { friendlyError, notify } from '../utils/feedback'

const http = axios.create({
  baseURL: '/api',
  timeout: 300000,
  // 登录态基于会话 Cookie，跨域/代理场景需携带凭据
  withCredentials: true
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'success' in body) {
      if (!body.success) {
        notify.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message))
      }
      return body.data
    }
    return body
  },
  (err) => {
    // 登录态失效：清理本地用户信息并跳转登录页，避免停留在无数据的页面
    if (err.response?.status === 401) {
      handleUnauthorized()
      return Promise.reject(err)
    }
    // 单个请求可通过 config.silent = true 接管自己的错误处理
    const silent = err.config?.silent === true
    const message = friendlyError(err)
    if (!silent && message) notify.error(message)
    return Promise.reject(err)
  }
)

/** 登录态失效处理：跳转登录页（带回跳地址），避免循环重定向 */
function handleUnauthorized() {
  if (typeof window === 'undefined') return
  if (window.location.pathname === '/login') return
  const redirect = window.location.pathname + window.location.search
  window.location.href = '/login?redirect=' + encodeURIComponent(redirect)
}

export default {
  // 登录认证
  authStatus: () => http.get('/auth/status', { silent: true }),
  login: (data) => http.post('/auth/login', data, { silent: true }),
  logout: () => http.post('/auth/logout', null, { silent: true }),
  changePassword: (data) => http.put('/auth/password', data, { silent: true }),
  // 科目章节
  listSubjects: () => http.get('/subjects'),
  createSubject: (data) => http.post('/subjects', data),
  updateSubject: (id, data) => http.put(`/subjects/${id}`, data),
  deleteSubject: (id) => http.delete(`/subjects/${id}`),
  createChapter: (subjectId, data) => http.post(`/subjects/${subjectId}/chapters`, data),
  updateChapter: (chapterId, data) => http.put(`/subjects/chapters/${chapterId}`, data),
  deleteChapter: (chapterId) => http.delete(`/subjects/chapters/${chapterId}`),
  // 题目
  pageQuestions: (params) => http.get('/questions', { params }),
  getQuestion: (id) => http.get(`/questions/${id}`),
  createQuestion: (data) => http.post('/questions', data),
  updateQuestion: (id, data) => http.put(`/questions/${id}`, data),
  deleteQuestion: (id) => http.delete(`/questions/${id}`),
  batchDeleteQuestions: (ids) => http.post('/questions/batch-delete', { ids }),
  fetchPractice: (data) => http.post('/questions/practice', data),
  // 刷题
  submitAnswer: (data) => http.post('/practice/submit', data),
  history: (params) => http.get('/practice/history', { params }),
  // 错题本
  wrongList: (params) => http.get('/wrong-book', { params }),
  removeWrong: (questionId) => http.delete(`/wrong-book/${questionId}`),
  clearWrong: (params) => http.delete('/wrong-book', { params }),
  // 收藏
  favoriteList: (params) => http.get('/favorites', { params }),
  addFavorite: (questionId) => http.post(`/favorites/${questionId}`),
  removeFavorite: (questionId) => http.delete(`/favorites/${questionId}`),
  // 统计
  stats: () => http.get('/stats'),
  // 导入导出
  importUrl: '/api/import/upload',
  templateUrl: '/api/import/template',
  exportUrl: '/api/import/export',
  // AI
  aiStatus: () => http.get('/ai/status'),
  updateAiConfig: (data) => http.put('/ai/config', data),
  testAiConnection: () => http.post('/ai/test'),
  aiGenerate: (data) => http.post('/ai/generate', data),
  aiConfirm: (data) => http.post('/ai/confirm', data)
}
