import puppeteer from 'puppeteer-core'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: 'new',
  args: ['--no-sandbox', '--disable-gpu']
})

const page = await browser.newPage()
const errors = []
page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE: ' + m.text()) })
page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message))
page.on('requestfailed', r => errors.push('REQFAIL: ' + r.url() + ' ' + r.failure()?.errorText))

async function clickByText(text) {
  return page.evaluate((t) => {
    const el = Array.from(document.querySelectorAll('button'))
      .find(b => b.textContent && b.textContent.includes(t) && !b.disabled)
    if (el) { el.click(); return true }
    return false
  }, text)
}

try {
  await page.goto('http://localhost:5173/practice', { waitUntil: 'networkidle2' })
  await new Promise(r => setTimeout(r, 1500))

  console.log('--- 1. 点击「开始练习」---')
  const started = await clickByText('开始练习')
  console.log('clicked =', started)
  await new Promise(r => setTimeout(r, 2500))

  const stem = await page.evaluate(() => {
    const el = document.querySelector('main .whitespace-pre-wrap')
    return el ? el.textContent.trim().slice(0, 40) : null
  })
  console.log('题干 =', stem)

  console.log('--- 2. 选择第一个选项 ---')
  const picked = await page.evaluate(() => {
    const opt = Array.from(document.querySelectorAll('button')).find(b => {
      const s = b.querySelector('span')
      return s && /^[A-F]$/.test(s.textContent.trim())
    })
    if (opt) { opt.click(); return opt.textContent.trim().slice(0, 1) }
    return null
  })
  console.log('选中 =', picked)

  console.log('--- 3. 提交答案 ---')
  console.log('clicked =', await clickByText('提交答案'))
  await new Promise(r => setTimeout(r, 2500))

  const feedback = await page.evaluate(() => {
    const t = document.querySelector('main')?.innerText || ''
    return {
      wrong: t.includes('回答错误'),
      right: t.includes('回答正确'),
      snippet: t.split('\n').filter(l => l.includes('回答')).slice(0, 3).join(' | ')
    }
  })
  console.log('反馈 =', JSON.stringify(feedback, null, 0))

  const wrongBook = await page.evaluate(async () => {
    const r = await fetch('/api/wrong-book')
    const j = await r.json()
    return (j.data || []).map(q => q.id)
  })
  console.log('错题本 ids =', JSON.stringify(wrongBook))

  const history = await page.evaluate(async () => {
    const r = await fetch('/api/practice/history?page=1&size=3')
    const j = await r.json()
    return (j.data?.content || []).map(h => ({ q: h.questionId, ok: h.correct, a: h.userAnswer }))
  })
  console.log('最近答题记录 =', JSON.stringify(history))
} catch (e) {
  console.log('=== E2E FAILED ===')
  console.log(e && e.stack ? e.stack : e)
} finally {
  console.log('--- 浏览器错误 ---')
  console.log(errors.length ? errors.join('\n') : '(无)')
  await browser.close()
}
