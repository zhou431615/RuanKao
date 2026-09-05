import { defineStore } from 'pinia'
import api from '../api'

export const useAppStore = defineStore('app', {
  state: () => ({
    subjects: [],
    loaded: false,
    aiConfigured: false,
    aiStatus: null
  }),
  actions: {
    async loadSubjects(force = false) {
      if (this.loaded && !force) return this.subjects
      this.subjects = await api.listSubjects()
      this.loaded = true
      return this.subjects
    },
    async loadAiStatus() {
      const s = await api.aiStatus()
      this.aiConfigured = s.configured
      this.aiStatus = s
      return s
    },
    invalidate() {
      this.loaded = false
    }
  }
})
