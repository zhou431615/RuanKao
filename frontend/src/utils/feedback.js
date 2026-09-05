import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 统一的人机反馈入口。
 *
 * 约定：错误提示统一由 api/index.js 的响应拦截器负责，业务代码只负责成功提示，
 * 避免同一错误弹出两次。确实需要自定义错误文案时再显式调用 notify.error。
 */
export const notify = {
  success(message) {
    return ElMessage({ type: 'success', message, showClose: true, duration: 2200 })
  },
  info(message) {
    return ElMessage({ type: 'info', message, showClose: true, duration: 2600 })
  },
  warning(message) {
    return ElMessage({ type: 'warning', message, showClose: true, duration: 3000 })
  },
  error(message) {
    return ElMessage({ type: 'error', message, showClose: true, duration: 4500 })
  }
}

/** 将异常转换为用户能看懂的中文提示 */
export function friendlyError(error) {
  if (!error) return '操作失败，请稍后重试'

  // 请求被取消（如组件卸载）：无需打扰用户
  if (error.code === 'ERR_CANCELED') return ''

  const status = error.response?.status
  const serverMessage = error.response?.data?.message

  if (!error.response) {
    if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
      return '请求超时了，请检查网络或稍后重试'
    }
    return '无法连接服务器，请确认后端已启动（默认 http://localhost:8080）'
  }

  switch (status) {
    case 400:
      // 业务校验失败：后端已给出具体原因，直接透传
      return serverMessage || '提交的信息有误，请检查后重试'
    case 401:
      return '登录状态已失效，请重新登录'
    case 403:
      return '没有权限执行该操作'
    case 404:
      return '请求的内容不存在，可能已被删除'
    case 413:
      return '上传的文件过大，请拆分为多个小文件后再试'
    case 429:
      return '操作太频繁了，请稍后再试'
    case 500:
      return '服务器开小差了，请稍后重试'
    case 502:
    case 503:
    case 504:
      return '服务暂时不可用，请稍后重试'
    default:
      return serverMessage || `请求失败（${status}），请稍后重试`
  }
}

/**
 * 危险操作二次确认。
 * @param {string} message 说明文本
 * @param {object} options { title, confirmText, cancelText, danger }
 */
export function confirmAction(message, options = {}) {
  const {
    title = '请确认',
    confirmText = '确定',
    cancelText = '取消',
    danger = true
  } = options

  return ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText: confirmText,
    cancelButtonText: cancelText,
    confirmButtonClass: danger ? 'el-button--danger' : 'el-button--primary',
    closeOnClickModal: false,
    // 用户点「取消 / 关闭」时 ElMessageBox 会 reject，这里吞掉避免未处理拒绝
    distinguishCancelAndClose: false
  })
}

/**
 * 包装异步操作：自动维护 loading 态，成功后给出提示，失败时交由拦截器统一提示。
 * @param {import('vue').Ref<boolean>} loadingRef loading 响应式变量
 * @param {Function} task 异步任务
 * @param {object} options { success 成功提示文案 }
 */
export async function withLoading(loadingRef, task, options = {}) {
  loadingRef.value = true
  try {
    const result = await task()
    if (options.success) notify.success(options.success)
    return result
  } finally {
    loadingRef.value = false
  }
}
