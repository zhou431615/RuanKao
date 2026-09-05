/**
 * 下载后端返回的文件，避免被浏览器弹窗拦截。
 */
export async function downloadFile(url, fallbackName) {
  const response = await fetch(url)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `下载失败（${response.status}）`)
  }
  const blob = await response.blob()
  const disposition = response.headers.get('content-disposition') || ''
  const encodedMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
  const filename = encodedMatch
    ? decodeURIComponent(encodedMatch[1])
    : plainMatch?.[1] || fallbackName

  const urlObject = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = urlObject
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(urlObject), 1000)
}
