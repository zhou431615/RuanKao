const { app, BrowserWindow, Menu, dialog, ipcMain, shell } = require('electron')
const path = require('path')
const { spawn, execSync, spawnSync } = require('child_process')
const http = require('http')
const net = require('net')
const fs = require('fs')

let mainWindow = null
let splashWindow = null
let backendProcess = null
let localServer = null
let localServerPort = null
let backendLogs = []
let logStream = null
let isQuitting = false

const isDev = !app.isPackaged

const BACKEND_PORT = 8080
const LOCAL_SERVER_PORT_START = 9527
const LOCAL_SERVER_PORT_MAX_ATTEMPTS = 100
const MAX_BACKEND_LOG_LINES = 200
const JAVA_PROBE_TIMEOUT = 10000

const MIME_TYPES = {
  '.html': 'text/html',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.eot': 'application/vnd.ms-fontobject',
  '.map': 'application/json'
}

function getUserDataDir() {
  try {
    return app.getPath('userData')
  } catch (_) {
    return path.join(
      process.env.APPDATA || path.join(process.env.HOME || process.env.USERPROFILE, 'AppData', 'Roaming'),
      '软考刷题'
    )
  }
}

function getLogFilePath() {
  return path.join(getUserDataDir(), 'app.log')
}

function initLogFile() {
  try {
    const dir = getUserDataDir()
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true })
    }
    const logPath = getLogFilePath()
    if (fs.existsSync(logPath)) {
      try {
        const stat = fs.statSync(logPath)
        if (stat.size > 2 * 1024 * 1024) {
          const backupPath = logPath + '.old'
          if (fs.existsSync(backupPath)) fs.unlinkSync(backupPath)
          fs.renameSync(logPath, backupPath)
        }
      } catch (_) {}
    }
    logStream = fs.createWriteStream(logPath, { flags: 'a' })
  } catch (_) {}
}

function log(level, message) {
  const timestamp = new Date().toISOString()
  const line = `[${timestamp}] [${level}] ${message}`
  if (logStream) {
    try { logStream.write(line + '\n') } catch (_) {}
  }
  if (isDev) {
    if (level === 'ERROR') console.error(line)
    else console.log(line)
  }
}

process.on('uncaughtException', (err) => {
  log('ERROR', `Uncaught exception: ${err.message}\n${err.stack}`)
  try {
    dialog.showErrorBox('应用异常', `程序发生未预期的错误：\n\n${err.message}\n\n日志文件：${getLogFilePath()}`)
  } catch (_) {}
})

process.on('unhandledRejection', (reason) => {
  log('ERROR', `Unhandled rejection: ${reason}`)
})

const gotTheLock = app.requestSingleInstanceLock()

if (!gotTheLock) {
  app.quit()
  return
}

app.setAppUserModelId('com.ruankao.app')

initLogFile()
log('INFO', `App starting, isDev=${isDev}, version=${app.getVersion()}`)
log('INFO', `userData: ${getUserDataDir()}`)

function isPortAvailable(port) {
  return new Promise((resolve) => {
    const tester = net.createServer()
    tester.once('error', () => resolve(false))
    tester.once('close', () => resolve(true))
    tester.listen(port, () => tester.close())
  })
}

async function findAvailablePort(startPort, maxAttempts) {
  for (let port = startPort; port < startPort + maxAttempts; port++) {
    if (await isPortAvailable(port)) {
      return port
    }
  }
  throw new Error(`No available port found in range ${startPort}-${startPort + maxAttempts - 1}`)
}


function getJavaCachePath() {
  return path.join(getUserDataDir(), '.java-path.cache')
}

function getJavaMajor(javaPath) {
  let output = ''
  let probeError = null
  try {
    const result = spawnSync(javaPath, ['-version'], {
      encoding: 'utf8',
      timeout: JAVA_PROBE_TIMEOUT,
      windowsHide: true
    })
    probeError = result.error
    output = result.stdout || result.stderr || ''

    // spawnSync 在某些环境下会失败（ENOENT / 权限 / 沙箱），退回到 cmd 执行
    if (!output) {
      try {
        output = execSync(`"${javaPath}" -version 2>&1`, {
          encoding: 'utf8',
          timeout: JAVA_PROBE_TIMEOUT,
          windowsHide: true
        }) || ''
        probeError = null
      } catch (e) {
        output = (e.stdout || '') + (e.stderr || '')
      }
    }
  } catch (err) {
    probeError = err
  }

  const quoted = output.split('"')[1] || ''
  const major = parseInt(quoted, 10) || 0
  if (major <= 0) {
    log('WARN', `Java probe failed: ${javaPath} | error=${probeError ? probeError.message : 'none'} | output=${JSON.stringify(output).slice(0, 200)}`)
  }
  return major
}

function queryRegistryJavaHomes() {
  const homes = []
  if (process.platform !== 'win32') return homes
  const roots = [
    'HKLM\\SOFTWARE\\JavaSoft\\JDK',
    'HKLM\\SOFTWARE\\JavaSoft\\JRE',
    'HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft\\JDK'
  ]
  for (const root of roots) {
    try {
      const out = execSync(`reg query "${root}" /s /v JavaHome`, {
        encoding: 'utf8',
        timeout: 8000,
        windowsHide: true
      })
      for (const line of out.split(/\r?\n/)) {
        const match = line.match(/JavaHome\s+REG_SZ\s+(.+)$/)
        if (match) {
          homes.push(match[1].trim())
        }
      }
    } catch (_) {
      // 注册表项不存在或无权限，忽略
    }
  }
  return homes
}

function resolveJavaFromPath() {
  try {
    const cmd = process.platform === 'win32' ? 'where java' : 'which java'
    const out = execSync(cmd, { encoding: 'utf8', timeout: 8000, windowsHide: true })
    const found = out.split(/\r?\n/).map((s) => s.trim()).find((s) => s && fs.existsSync(s))
    return found || null
  } catch (_) {
    return null
  }
}

function collectJavaCandidates() {
  const candidates = []
  const push = (p) => {
    if (p && candidates.indexOf(p) === -1) candidates.push(p)
  }

  for (const envName of ['JAVA_HOME', 'JDK_HOME']) {
    const home = process.env[envName]
    if (!home) continue
    push(path.join(home, 'bin', 'java.exe'))
    push(path.join(home, 'bin', 'java'))
  }

  const programFiles = process.env['ProgramFiles'] || 'C:\\Program Files'
  const programFilesX86 = process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)'
  const localAppData = process.env['LOCALAPPDATA'] || ''
  const userProfile = process.env.USERPROFILE || process.env.HOME || ''

  const javaDirs = [
    path.join(userProfile, '.jdks'),
    path.join(userProfile, '.gradle', 'jdks'),
    path.join(userProfile, 'scoop', 'apps'),
    path.join(programFiles, 'Java'),
    path.join(programFilesX86, 'Java'),
    path.join(programFiles, 'Amazon Corretto'),
    path.join(programFiles, 'Eclipse Adoptium'),
    path.join(programFiles, 'Microsoft'),
    path.join(programFiles, 'BellSoft'),
    path.join(programFiles, 'Zulu'),
    path.join(programFiles, 'GraalVM'),
    path.join(localAppData, 'Programs'),
    'C:\\Program Files\\Amazon Corretto',
    'C:\\Program Files\\Eclipse Adoptium',
    'C:\\Program Files\\Microsoft',
    'C:\\Program Files\\BellSoft'
  ]

  for (const dir of javaDirs) {
    if (!dir || !fs.existsSync(dir)) continue
    let entries = []
    try {
      entries = fs.readdirSync(dir)
    } catch (_) {
      continue
    }
    for (const entry of entries) {
      const base = path.join(dir, entry)
      push(path.join(base, 'bin', 'java.exe'))
      push(path.join(base, 'bin', 'java'))
      push(path.join(base, 'Contents', 'Home', 'bin', 'java.exe'))
      push(path.join(base, 'Contents', 'Home', 'bin', 'java'))
      push(path.join(base, 'jre', 'bin', 'java.exe'))
    }
  }

  for (const home of queryRegistryJavaHomes()) {
    push(path.join(home, 'bin', 'java.exe'))
  }

  const pathJava = resolveJavaFromPath()
  if (pathJava) push(pathJava)

  log('INFO', `Java candidate dirs scanned, ${candidates.length} candidates`)
  return candidates
}

function findJava() {
  const cached = readCachedJavaPath()
  if (cached) {
    log('INFO', 'Java (cached): ' + cached)
    return cached
  }

  const candidates = collectJavaCandidates()
  let probed = 0
  for (const candidate of candidates) {
    let exists = false
    try {
      exists = fs.existsSync(candidate)
    } catch (_) {
      continue
    }
    if (!exists) continue
    probed++
    if (getJavaMajor(candidate) >= 17) {
      saveCachedJavaPath(candidate)
      log('INFO', 'Java found: ' + candidate)
      return candidate
    }
  }

  log('INFO', `Java candidates probed: ${probed}`)

  if (getJavaMajor('java') >= 17) {
    log('INFO', 'Java found in PATH')
    return 'java'
  }

  log('ERROR', 'Java 17+ not found')
  return null
}

function readCachedJavaPath() {
  try {
    const cachePath = getJavaCachePath()
    if (fs.existsSync(cachePath)) {
      const cached = fs.readFileSync(cachePath, 'utf8').trim()
      if (cached && fs.existsSync(cached)) {
        if (getJavaMajor(cached) >= 17) {
          return cached
        }
      }
    }
  } catch (_) {}
  return null
}

function saveCachedJavaPath(javaPath) {
  try {
    const cachePath = getJavaCachePath()
    const dir = path.dirname(cachePath)
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true })
    }
    fs.writeFileSync(cachePath, javaPath, 'utf8')
  } catch (_) {}
}

function getBackendJarPath() {
  if (isDev) {
    return path.join(__dirname, '..', '..', 'build', 'libs', 'RuanKao-1.0.0.jar')
  }
  return path.join(process.resourcesPath, 'backend', 'RuanKao-1.0.0.jar')
}

function getBackendCwd() {
  if (isDev) {
    return path.join(__dirname, '..', '..')
  }
  return getUserDataDir()
}

function getDataDir() {
  return path.join(getBackendCwd(), 'data')
}

function appendBackendLog(source, data) {
  const lines = data.toString().split('\n').filter(l => l.trim())
  for (const line of lines) {
    const entry = `[${source}] ${line}`
    backendLogs.push(entry)
    log('BACKEND', entry)
  }
  if (backendLogs.length > MAX_BACKEND_LOG_LINES) {
    backendLogs = backendLogs.slice(-MAX_BACKEND_LOG_LINES)
  }
}

function startBackend() {
  const jarPath = getBackendJarPath()
  const cwd = getBackendCwd()

  log('INFO', `Backend JAR: ${jarPath}`)
  log('INFO', `Backend CWD: ${cwd}`)

  if (!fs.existsSync(jarPath)) {
    const msg = `后端 JAR 文件不存在：${jarPath}`
    log('ERROR', msg)
    return { success: false, error: msg }
  }

  if (!fs.existsSync(cwd)) {
    fs.mkdirSync(cwd, { recursive: true })
  }

  const dataDir = getDataDir()
  if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true })
  }

  const javaPath = findJava()
  if (!javaPath) {
    return {
      success: false,
      error: '未找到 Java 17+ 运行环境。\n\n请安装 JDK 17 或更高版本后重试。\n推荐下载：Amazon Corretto 21\nhttps://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html'
    }
  }

  const jvmArgs = [
    '-Xms128m',
    '-Xmx512m',
    '-XX:+UseG1GC',
    '-XX:MaxGCPauseMillis=200',
    '-XX:ParallelGCThreads=2',
    '-Dspring.main.lazy-initialization=true',
    '-jar', jarPath
  ]

  log('INFO', `Starting backend: ${javaPath} ${jvmArgs.join(' ')}`)

  try {
    backendProcess = spawn(javaPath, jvmArgs, {
      cwd,
      stdio: 'pipe',
      env: { ...process.env }
    })
  } catch (err) {
    const msg = `启动后端进程失败：${err.message}`
    log('ERROR', msg)
    return { success: false, error: msg }
  }

  backendProcess.stdout.on('data', (data) => {
    appendBackendLog('out', data)
  })
  backendProcess.stderr.on('data', (data) => {
    appendBackendLog('err', data)
  })
  backendProcess.on('error', (err) => {
    appendBackendLog('err', `spawn error: ${err.message}`)
  })
  backendProcess.on('close', (code) => {
    appendBackendLog('main', `Backend exited with code ${code}`)
    backendProcess = null
  })

  return { success: true }
}

function stopBackend() {
  if (!backendProcess) return
  try {
    if (process.platform === 'win32') {
      const pid = backendProcess.pid
      if (pid) {
        try {
          execSync(`taskkill /F /T /PID ${pid}`, { stdio: 'ignore', timeout: 5000 })
        } catch (_) {
          backendProcess.kill()
        }
      }
    } else {
      backendProcess.kill('SIGTERM')
    }
  } catch (_) {
    try { backendProcess.kill() } catch (_) {}
  }
  backendProcess = null
}

function waitForBackend(maxRetries, initialInterval) {
  maxRetries = maxRetries || 80
  initialInterval = initialInterval || 300
  return new Promise((resolve, reject) => {
    let retries = 0
    const check = () => {
      if (isQuitting) {
        reject(new Error('App is quitting'))
        return
      }
      const req = http.get(`http://127.0.0.1:${BACKEND_PORT}/api/health`, (res) => {
        res.resume()
        log('INFO', 'Backend is ready')
        resolve()
      })
      req.on('error', () => {
        retries++
        if (retries >= maxRetries) {
          reject(new Error('Backend did not start in time'))
        } else {
          const interval = Math.min(initialInterval + retries * 20, 2000)
          setTimeout(check, interval)
        }
      })
      req.setTimeout(1500, () => {
        req.destroy()
      })
    }
    check()
  })
}

function startLocalServer() {
  return new Promise((resolve, reject) => {
    const distDir = path.join(__dirname, '..', 'dist')

    if (!fs.existsSync(distDir)) {
      const msg = `Frontend dist directory not found: ${distDir}`
      log('ERROR', msg)
      reject(new Error(msg))
      return
    }

    const server = http.createServer((req, res) => {
      if (req.url.startsWith('/api')) {
        proxyToBackend(req, res)
        return
      }

      let filePath = path.join(distDir, req.url === '/' ? 'index.html' : req.url)
      const ext = path.extname(filePath).toLowerCase()
      const contentType = MIME_TYPES[ext] || 'application/octet-stream'

      fs.readFile(filePath, (err, data) => {
        if (err) {
          fs.readFile(path.join(distDir, 'index.html'), (fallbackErr, fallbackData) => {
            if (fallbackErr) {
              res.writeHead(404)
              res.end('Not Found')
              return
            }
            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
            res.end(fallbackData)
          })
          return
        }
        res.writeHead(200, { 'Content-Type': contentType })
        res.end(data)
      })
    })

    server.on('error', (err) => {
      if (err.code === 'EADDRINUSE') {
        reject(new Error(`Port ${localServerPort} is already in use`))
      } else {
        reject(err)
      }
    })

    server.listen(localServerPort, '127.0.0.1', () => {
      localServer = server
      log('INFO', `Local server started on 127.0.0.1:${localServerPort}`)
      resolve()
    })
  })
}

function proxyToBackend(req, res) {
  const options = {
    hostname: '127.0.0.1',
    port: BACKEND_PORT,
    path: req.url,
    method: req.method,
    headers: Object.assign({}, req.headers, { host: `127.0.0.1:${BACKEND_PORT}` })
  }

  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers)
    proxyRes.pipe(res)
  })

  proxyReq.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ success: false, message: '后端服务不可用' }))
  })

  req.pipe(proxyReq)
}

function stopLocalServer() {
  if (localServer) {
    localServer.close()
    localServer = null
  }
}

async function clearDatabase() {
  const dataDir = getDataDir()

  if (!fs.existsSync(dataDir)) {
    return { success: true, message: '数据目录不存在，无需清除' }
  }

  const files = fs.readdirSync(dataDir)
  const dbFiles = files.filter((f) =>
    f.startsWith('ruankao.') && (f.endsWith('.mv.db') || f.endsWith('.lock.db') || f.endsWith('.trace.db'))
  )

  if (dbFiles.length === 0) {
    return { success: true, message: '未找到数据库文件' }
  }

  stopBackend()

  await new Promise((resolve) => setTimeout(resolve, 1500))

  const deleted = []
  const failed = []

  for (const file of dbFiles) {
    const filePath = path.join(dataDir, file)
    try {
      fs.unlinkSync(filePath)
      deleted.push(file)
    } catch (err) {
      failed.push({ file, error: err.message })
    }
  }

  if (!isDev) {
    const startResult = startBackend()
    if (!startResult.success) {
      return { success: false, message: `数据库已删除，但后端重启失败：${startResult.error}`, deleted, failed }
    }
    try {
      await waitForBackend()
    } catch (err) {
      return { success: false, message: `数据库已删除，但后端重启失败：${err.message}`, deleted, failed }
    }
  }

  if (failed.length > 0) {
    return { success: false, message: '部分文件删除失败', deleted, failed }
  }

  return { success: true, message: `已删除 ${deleted.length} 个数据库文件，后端已重启`, deleted }
}

function getIconPath() {
  const iconPath = path.join(__dirname, 'icon.png')
  try {
    if (fs.existsSync(iconPath)) {
      return iconPath
    }
  } catch (_) {}
  return undefined
}

function createSplashWindow() {
  try {
    splashWindow = new BrowserWindow({
      width: 420,
      height: 280,
      frame: false,
      resizable: false,
      center: true,
      alwaysOnTop: true,
      skipTaskbar: true,
      show: false,
      backgroundColor: '#6C5CE7',
      webPreferences: {
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: false
      }
    })

    splashWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(SPLASH_HTML))
    splashWindow.once('ready-to-show', () => {
      try { splashWindow.show() } catch (_) {}
    })
    splashWindow.on('closed', () => {
      splashWindow = null
    })
  } catch (err) {
    log('ERROR', 'Failed to create splash window: ' + err.message)
    splashWindow = null
  }
}

function closeSplashWindow() {
  try {
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.close()
    }
  } catch (_) {}
  splashWindow = null
}

var SPLASH_HTML = '<!DOCTYPE html>' +
'<html><head><meta charset="utf-8"><style>' +
'* { margin: 0; padding: 0; box-sizing: border-box; }' +
'body { width: 420px; height: 280px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; flex-direction: column; align-items: center; justify-content: center; font-family: -apple-system, "Microsoft YaHei", sans-serif; color: white; overflow: hidden; }' +
'.logo { font-size: 36px; font-weight: 700; margin-bottom: 8px; letter-spacing: 4px; }' +
'.subtitle { font-size: 13px; opacity: 0.8; margin-bottom: 28px; }' +
'.spinner { width: 32px; height: 32px; border: 3px solid rgba(255,255,255,0.3); border-top-color: white; border-radius: 50%; animation: spin 0.8s linear infinite; margin-bottom: 16px; }' +
'@keyframes spin { to { transform: rotate(360deg); } }' +
'.status { font-size: 12px; opacity: 0.7; }' +
'</style></head><body>' +
'<div class="logo">软考刷题</div>' +
'<div class="subtitle">计算机技术与软件专业技术资格考试</div>' +
'<div class="spinner"></div>' +
'<div class="status">正在启动服务，请稍候...</div>' +
'</body></html>'

function buildAppMenu() {
  const template = [
    {
      label: '数据',
      submenu: [
        {
          label: '打开数据目录',
          click: () => {
            const dataDir = getDataDir()
            if (!fs.existsSync(dataDir)) {
              fs.mkdirSync(dataDir, { recursive: true })
            }
            shell.openPath(dataDir)
          }
        },
        {
          label: '清除数据库',
          click: async () => {
            if (!mainWindow || mainWindow.isDestroyed()) return
            const result = await dialog.showMessageBox(mainWindow, {
              type: 'warning',
              title: '清除数据库',
              message: '确定要删除所有数据库文件吗？',
              detail: '此操作将清除所有题目、答题记录、错题本和收藏数据，且不可恢复。\n应用将自动重启后端服务。',
              buttons: ['取消', '确定清除'],
              defaultId: 0,
              cancelId: 0
            })
            if (result.response === 1) {
              const clearResult = await clearDatabase()
              if (mainWindow && !mainWindow.isDestroyed()) {
                dialog.showMessageBox(mainWindow, {
                  type: clearResult.success ? 'info' : 'error',
                  title: '清除数据库',
                  message: clearResult.success ? '清除完成' : '清除失败',
                  detail: clearResult.message,
                  buttons: ['确定']
                })
              }
              if (clearResult.success && mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.reload()
              }
            }
          }
        },
        { type: 'separator' },
        {
          label: '导出数据库备份',
          click: () => {
            if (!mainWindow || mainWindow.isDestroyed()) return
            const dataDir = getDataDir()
            dialog.showOpenDialog(mainWindow, {
              title: '选择备份保存位置',
              defaultPath: app.getPath('documents'),
              properties: ['openDirectory']
            }).then((result) => {
              if (result.canceled || result.filePaths.length === 0) return
              const targetDir = result.filePaths[0]
              const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
              const backupDir = path.join(targetDir, 'ruankao-backup-' + timestamp)
              fs.mkdirSync(backupDir, { recursive: true })

              if (!fs.existsSync(dataDir)) return
              const files = fs.readdirSync(dataDir)
              let count = 0
              for (const file of files) {
                if (file.startsWith('ruankao.') && (file.endsWith('.mv.db') || file.endsWith('.lock.db'))) {
                  fs.copyFileSync(path.join(dataDir, file), path.join(backupDir, file))
                  count++
                }
              }
              if (mainWindow && !mainWindow.isDestroyed()) {
                dialog.showMessageBox(mainWindow, {
                  type: 'info',
                  title: '备份完成',
                  message: '已备份 ' + count + ' 个文件到：',
                  detail: backupDir,
                  buttons: ['确定']
                })
              }
            })
          }
        }
      ]
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '查看后端日志',
          click: () => {
            if (!mainWindow || mainWindow.isDestroyed()) return
            const logs = backendLogs.length > 0
              ? backendLogs.join('\n')
              : '暂无后端日志'
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: '后端日志（最近 ' + MAX_BACKEND_LOG_LINES + ' 行）',
              message: '后端日志',
              detail: logs,
              buttons: ['确定']
            })
          }
        },
        {
          label: '打开日志文件',
          click: () => {
            const logPath = getLogFilePath()
            const dir = path.dirname(logPath)
            if (!fs.existsSync(dir)) {
              fs.mkdirSync(dir, { recursive: true })
            }
            if (!fs.existsSync(logPath)) {
              fs.writeFileSync(logPath, '', 'utf8')
            }
            shell.openPath(logPath)
          }
        },
        { type: 'separator' },
        {
          label: '关于',
          click: () => {
            if (!mainWindow || mainWindow.isDestroyed()) return
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: '关于 软考刷题',
              message: '软考刷题 v1.0.0',
              detail: '作者：千叶\n\n数据目录：' + getDataDir() + '\n日志文件：' + getLogFilePath(),
              buttons: ['确定']
            })
          }
        }
      ]
    }
  ]

  if (isDev) {
    template.push({
      label: '开发',
      submenu: [
        { role: 'toggleDevTools' },
        { role: 'reload' }
      ]
    })
  }

  return Menu.buildFromTemplate(template)
}

function createWindow(options) {
  options = options || {}
  const iconPath = getIconPath()
  const windowOptions = {
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    show: false,
    backgroundColor: '#f5f7fa',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  }
  if (iconPath) {
    windowOptions.icon = iconPath
  }

  mainWindow = new BrowserWindow(windowOptions)

  // 无论页面是否加载成功，都要保证窗口最终可见，避免出现"进程在后台但没有界面"
  let showTimer = null
  function ensureWindowVisible() {
    if (showTimer) {
      clearTimeout(showTimer)
      showTimer = null
    }
    closeSplashWindow()
    try {
      if (mainWindow && !mainWindow.isDestroyed()) {
        if (!mainWindow.isVisible()) mainWindow.show()
        mainWindow.focus()
      }
    } catch (_) {}
  }

  showTimer = setTimeout(ensureWindowVisible, 8000)
  mainWindow.once('ready-to-show', ensureWindowVisible)
  mainWindow.webContents.once('did-finish-load', ensureWindowVisible)
  mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    log('ERROR', `Page load failed: ${validatedURL} code=${errorCode} ${errorDescription}`)
    ensureWindowVisible()
  })

  if (options.errorPage) {
    mainWindow.loadFile(options.errorPage)
  } else if (isDev) {
    mainWindow.loadURL('http://localhost:5173')
    mainWindow.webContents.openDevTools()
  } else {
    mainWindow.loadURL('http://127.0.0.1:' + localServerPort)
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  Menu.setApplicationMenu(buildAppMenu())
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function buildErrorPageHtml(title, detail) {
  return '<!DOCTYPE html>' +
    '<html lang="zh-CN"><head><meta charset="utf-8">' +
    '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; script-src \'unsafe-inline\'">' +
    '<title>' + escapeHtml(title) + '</title>' +
    '<style>' +
    '* { margin: 0; padding: 0; box-sizing: border-box; }' +
    'body { min-height: 100vh; background: #f5f7fa; display: flex; align-items: center; justify-content: center; font-family: -apple-system, "Microsoft YaHei", sans-serif; color: #2c3e50; padding: 32px; }' +
    '.card { background: #fff; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.08); padding: 32px 36px; max-width: 760px; width: 100%; }' +
    '.badge { display: inline-block; background: #fee2e2; color: #b91c1c; font-size: 12px; padding: 4px 10px; border-radius: 999px; margin-bottom: 14px; }' +
    'h1 { font-size: 20px; margin-bottom: 18px; }' +
    '.detail { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; font-size: 13px; line-height: 1.8; white-space: pre-wrap; word-break: break-word; max-height: 320px; overflow: auto; }' +
    '.actions { margin-top: 24px; display: flex; gap: 12px; flex-wrap: wrap; }' +
    'button { border: none; border-radius: 8px; padding: 10px 20px; font-size: 14px; cursor: pointer; font-family: inherit; }' +
    '.primary { background: #6C5CE7; color: #fff; }' +
    '.primary:hover { background: #5b4bd6; }' +
    '.ghost { background: #eef2f7; color: #475569; }' +
    '.ghost:hover { background: #e2e8f0; }' +
    '.tip { margin-top: 18px; font-size: 12px; color: #94a3b8; line-height: 1.8; }' +
    '</style></head><body>' +
    '<div class="card">' +
    '<span class="badge">启动未完成</span>' +
    '<h1>' + escapeHtml(title) + '</h1>' +
    '<div class="detail" id="detail">' + escapeHtml(detail) + '</div>' +
    '<div class="actions">' +
    '<button class="primary" id="retry">重新尝试启动</button>' +
    '<button class="ghost" id="openLog">打开日志文件</button>' +
    '<button class="ghost" id="quit">退出</button>' +
    '</div>' +
    '<div class="tip">修复问题后点击「重新尝试启动」即可继续，无需重装应用。</div>' +
    '</div>' +
    '<script>\n' + ERROR_PAGE_SCRIPT + '\n</script>' +
    '</body></html>'
}

const ERROR_PAGE_SCRIPT = [
  "const api = window.electronAPI",
  "const detail = document.getElementById('detail')",
  "document.getElementById('retry').addEventListener('click', async () => {",
  "  const btn = document.getElementById('retry')",
  "  btn.disabled = true; btn.textContent = '正在启动…'",
  "  try {",
  "    const result = api ? await api.retryStartup() : { success: false, message: '接口不可用' }",
  "    if (result && result.success) { return }",
  "    if (detail && result) { detail.textContent = (result.message || '启动失败') }",
  "    btn.disabled = false; btn.textContent = '重新尝试启动'",
  "  } catch (e) {",
  "    if (detail) { detail.textContent = String(e && e.message ? e.message : e) }",
  "    btn.disabled = false; btn.textContent = '重新尝试启动'",
  "  }",
  "})",
  "document.getElementById('openLog').addEventListener('click', () => { api && api.openLogFile() })",
  "document.getElementById('quit').addEventListener('click', () => { api && api.quitApp() })"
].join('\n')

function showErrorPage(title, detail) {
  log('ERROR', `showErrorPage: ${title} - ${String(detail).slice(0, 500)}`)
  try {
    const dir = getUserDataDir()
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
    const pagePath = path.join(dir, 'error-page.html')
    fs.writeFileSync(pagePath, buildErrorPageHtml(title, detail), 'utf8')

    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.loadFile(pagePath)
      try { mainWindow.show(); mainWindow.focus() } catch (_) {}
      return
    }
    createWindow({ errorPage: pagePath })
  } catch (err) {
    log('ERROR', 'Failed to render error page: ' + err.message)
    try {
      dialog.showErrorBox(title, String(detail) + '\n\n日志：' + getLogFilePath())
    } catch (_) {}
  }
}

async function bootstrapBackendAndServer() {
  if (!backendProcess) {
    const startResult = startBackend()
    if (!startResult.success) return startResult
  }

  if (!localServer) {
    try {
      localServerPort = await findAvailablePort(LOCAL_SERVER_PORT_START, LOCAL_SERVER_PORT_MAX_ATTEMPTS)
      await startLocalServer()
    } catch (err) {
      log('ERROR', 'Failed to start local server: ' + err.message)
      return { success: false, message: `本地静态服务启动失败：${err.message}` }
    }
  }

  try {
    await waitForBackend()
  } catch (err) {
    log('ERROR', 'Backend startup timeout: ' + err.message)
    return {
      success: false,
      message: '后端服务未能在规定时间内就绪。\n\n可能原因：\n' +
        '- 未找到 Java 17+ 运行环境\n' +
        `- 端口 ${BACKEND_PORT} 被其他程序占用\n` +
        '- 后端启动异常\n\n' +
        '最近日志：\n' + (backendLogs.slice(-20).join('\n') || '（无日志）') +
        '\n\n完整日志：' + getLogFilePath()
    }
  }

  return { success: true }
}

ipcMain.handle('clear-database', async () => {
  return await clearDatabase()
})

ipcMain.handle('get-data-dir', () => {
  return getDataDir()
})

ipcMain.handle('open-data-dir', () => {
  const dataDir = getDataDir()
  if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true })
  }
  shell.openPath(dataDir)
})

ipcMain.handle('get-backend-logs', () => {
  return backendLogs.join('\n')
})

ipcMain.handle('open-log-file', () => {
  const logPath = getLogFilePath()
  try {
    if (!fs.existsSync(logPath)) {
      fs.writeFileSync(logPath, '', 'utf8')
    }
  } catch (_) {}
  shell.openPath(logPath)
})

ipcMain.handle('quit-app', () => {
  isQuitting = true
  app.quit()
})

ipcMain.handle('retry-startup', async () => {
  if (isDev) {
    return { success: true }
  }
  const result = await bootstrapBackendAndServer()
  if (!result.success) {
    return result
  }
  closeSplashWindow()
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.loadURL('http://127.0.0.1:' + localServerPort)
    try { mainWindow.show(); mainWindow.focus() } catch (_) {}
  } else {
    createWindow()
  }
  return { success: true }
})

app.on('second-instance', () => {
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.focus()
  }
})

app.whenReady().then(async () => {
  try {
    if (!isDev) {
      createSplashWindow()

      const startResult = startBackend()
      if (!startResult.success) {
        closeSplashWindow()
        showErrorPage('后端启动失败', startResult.error + '\n\n日志文件：' + getLogFilePath())
        return
      }

      try {
        localServerPort = await findAvailablePort(LOCAL_SERVER_PORT_START, LOCAL_SERVER_PORT_MAX_ATTEMPTS)
        await startLocalServer()
      } catch (err) {
        log('ERROR', 'Failed to start local server: ' + err.message)
        closeSplashWindow()
        showErrorPage('本地服务启动失败', err.message + '\n\n日志文件：' + getLogFilePath())
        return
      }

      try {
        await waitForBackend()
      } catch (err) {
        const timeoutResult = {
          success: false,
          message: '后端服务未能在规定时间内就绪。\n\n可能原因：\n' +
            '- 未找到 Java 17+ 运行环境\n' +
            `- 端口 ${BACKEND_PORT} 被其他程序占用\n` +
            '- 后端启动异常\n\n' +
            '最近日志：\n' + (backendLogs.slice(-20).join('\n') || '（无日志）') +
            '\n\n完整日志：' + getLogFilePath()
        }
        closeSplashWindow()
        showErrorPage('后端启动超时', timeoutResult.message)
        return
      }
    }

    createWindow()
  } catch (err) {
    log('ERROR', 'Bootstrap failed: ' + (err && err.stack ? err.stack : err))
    closeSplashWindow()
    showErrorPage('应用启动失败', String(err && err.message ? err.message : err) + '\n\n日志文件：' + getLogFilePath())
  }
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow()
  }
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('before-quit', () => {
  isQuitting = true
  stopLocalServer()
  stopBackend()
})

app.on('will-quit', () => {
  if (logStream) {
    try { logStream.end() } catch (_) {}
    logStream = null
  }
})