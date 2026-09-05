<template>
  <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 card-hover">
    <div class="flex items-start justify-between gap-4 mb-4">
      <div class="flex items-center gap-2 flex-wrap">
        <el-checkbox v-if="selectable" :model-value="selected" class="!mr-0"
          @change="$emit('update:selected', $event)" />
        <el-tag :type="typeMeta.color" effect="dark" size="small" round>{{ typeMeta.label }}</el-tag>
        <el-tag v-if="question.difficulty" size="small" effect="plain" round>
          难度 {{ '★'.repeat(question.difficulty) }}
        </el-tag>
        <span v-if="question.chapterName" class="text-xs text-gray-400">{{ question.chapterName }}</span>
      </div>
      <div class="flex items-center gap-1 shrink-0">
        <el-tooltip :content="question.favorite ? '取消收藏' : '收藏'">
          <button @click="$emit('toggle-favorite', question)"
            class="p-1.5 rounded-lg transition-colors cursor-pointer"
            :class="question.favorite ? 'text-amber-400 hover:bg-amber-50' : 'text-gray-300 hover:text-amber-400 hover:bg-amber-50'">
            <Star class="w-4.5 h-4.5" :fill="question.favorite ? 'currentColor' : 'none'" />
          </button>
        </el-tooltip>
        <el-tooltip v-if="showDelete" content="删除">
          <button @click="$emit('delete', question)" class="p-1.5 rounded-lg text-gray-300 hover:text-red-500 hover:bg-red-50 transition-colors cursor-pointer">
            <Trash2 class="w-4.5 h-4.5" />
          </button>
        </el-tooltip>
      </div>
    </div>
    <RichTextViewer :text="question.stem" class="text-[15px] leading-relaxed text-gray-800" />
    <div v-if="hasOptions" class="mt-4 space-y-2">
      <div v-for="(opt, key) in question.options" :key="key"
        class="flex gap-2.5 px-3.5 py-2.5 rounded-xl text-sm transition-colors"
        :class="optionClass(key)">
        <span class="font-semibold shrink-0">{{ key }}.</span>
        <RichTextViewer :text="opt" class="min-w-0 flex-1 text-gray-700" />
      </div>
    </div>
    <slot />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Star, Trash2 } from '@lucide/vue'
import RichTextViewer from './RichTextViewer.vue'

const props = defineProps({
  question: { type: Object, required: true },
  showDelete: { type: Boolean, default: false },
  /** 是否显示批量选择框 */
  selectable: { type: Boolean, default: false },
  /** 是否已被勾选（配合 selectable 使用） */
  selected: { type: Boolean, default: false },
  /** 已选中的选项键列表（判分展示用） */
  choiceSelected: { type: Array, default: () => [] },
  revealed: { type: Boolean, default: false },
  correctAnswer: { type: String, default: '' }
})
defineEmits(['toggle-favorite', 'delete', 'update:selected'])

const TYPE_META = {
  SINGLE: { label: '单选', color: 'primary' },
  MULTIPLE: { label: '多选', color: 'warning' },
  JUDGE: { label: '判断', color: 'success' },
  ESSAY: { label: '问答', color: 'info' }
}
const typeMeta = computed(() => TYPE_META[props.question.type] || { label: props.question.type, color: 'info' })
const hasOptions = computed(() => props.question.options && Object.keys(props.question.options).length > 0)

function optionClass(key) {
  const selected = props.choiceSelected.includes(key)
  const isCorrect = props.revealed && props.correctAnswer && props.correctAnswer.includes(key)
  const isWrongPick = props.revealed && selected && !isCorrect
  if (isCorrect) return 'bg-emerald-50 border border-emerald-300 text-emerald-700'
  if (isWrongPick) return 'bg-red-50 border border-red-300 text-red-600'
  if (selected) return 'bg-indigo-50 border border-primary text-primary'
  return 'bg-gray-50 border border-transparent hover:bg-indigo-50/60'
}
</script>
