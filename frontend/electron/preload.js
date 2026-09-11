const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('electronAPI', {
  platform: process.platform,
  clearDatabase: () => ipcRenderer.invoke('clear-database'),
  getDataDir: () => ipcRenderer.invoke('get-data-dir'),
  openDataDir: () => ipcRenderer.invoke('open-data-dir'),
  getBackendLogs: () => ipcRenderer.invoke('get-backend-logs'),
  openLogFile: () => ipcRenderer.invoke('open-log-file'),
  retryStartup: () => ipcRenderer.invoke('retry-startup'),
  quitApp: () => ipcRenderer.invoke('quit-app')
})