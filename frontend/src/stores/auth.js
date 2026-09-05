import { defineStore } from 'pinia'
import api from '../api'

/**
 * 登录态：默认视为「未确认」，由 ensureLoaded() 向后端核实后再决定放行。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    authenticated: false,
    /** 是否已向后端确认过登录态 */
    checked: false,
    loading: false
  }),
  actions: {
    async ensureLoaded() {
      if (this.checked) return this.authenticated
      this.loading = true
      try {
        const status = await api.authStatus()
        this.authenticated = !!status?.authenticated
        this.user = status?.user || null
      } catch {
        this.authenticated = false
        this.user = null
      } finally {
        this.checked = true
        this.loading = false
      }
      return this.authenticated
    },
    async login(payload) {
      const user = await api.login(payload)
      this.authenticated = true
      this.user = user
      this.checked = true
      return user
    },
    async logout() {
      try {
        await api.logout()
      } finally {
        this.authenticated = false
        this.user = null
        this.checked = true
      }
    },
    reset() {
      this.authenticated = false
      this.user = null
      this.checked = true
    }
  }
})
