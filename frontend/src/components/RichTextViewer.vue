<template>
  <div class="rich-text" v-html="html" />
</template>

<script setup>
defineOptions({ name: 'RichTextViewer' })

import { computed } from 'vue'

const props = defineProps({
  text: { type: String, default: '' }
})

const html = computed(() => renderRichText(props.text))

function renderRichText(value) {
  const escaped = String(value || '').replace(/[&<>"']/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]))

  return escaped
    .replace(/\r\n?/g, '\n')
    .replace(/\n/g, '<br>')
    .replace(/\[图:(.+?)\]/g, (_, rawUrl) => {
      const url = String(rawUrl).trim()
      if (!/^https?:\/\//i.test(url)) return ''
      const safeUrl = escapeAttribute(url)
      return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer" class="rich-image-link"><img src="${safeUrl}" alt="题目图片" loading="lazy" referrerpolicy="no-referrer"></a>`
    })
}

function escapeAttribute(value) {
  return String(value).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
</script>

<style scoped>
.rich-text :deep(.rich-image-link) {
  display: block;
  width: fit-content;
  max-width: 100%;
  margin: 0.75rem 0;
}

.rich-text :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 480px;
  object-fit: contain;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}
</style>
