<template>
  <div class="flex gap-5">
    <aside class="w-64 shrink-0 space-y-4">
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-4">
        <div class="flex items-center justify-between mb-3">
          <h3 class="font-semibold text-gray-800 text-sm">科目管理</h3>
          <el-button size="small" type="primary" circle title="新建科目" @click="subjectDialog = true">
            <Plus class="w-3.5 h-3.5" />
          </el-button>
        </div>
        <div class="space-y-1.5 max-h-96 overflow-y-auto">
          <div v-for="s in store.subjects" :key="s.id" @click="selectSubject(s)"
            class="px-3 py-2.5 rounded-xl cursor-pointer transition-all group"
            :class="filter.subjectId === s.id ? 'bg-primary text-white shadow-md shadow-indigo-200' : 'hover:bg-indigo-50'">
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium truncate">{{ s.name }}</span>
              <el-dropdown trigger="click" @command="cmd => subjectCommand(cmd, s)">
                <button class="opacity-0 group-hover:opacity-100 transition-opacity p-0.5 rounded hover:bg-black/10" @click.stop>
                  <MoreVertical class="w-3.5 h-3.5" />
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item command="addChapter">添加章节</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除科目</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <div class="text-xs mt-0.5" :class="filter.subjectId === s.id ? 'text-indigo-100' : 'text-gray-400'">
              {{ s.questionCount }} 题 · {{ (s.chapters || []).length }} 章节
            </div>
          </div>
        </div>
        <el-empty v-if="!store.subjects.length" description="暂无科目" :image-size="60" />
      </div>
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 space-y-2">
        <el-button class="!w-full" type="primary" plain @click="aiDialogOpen">
          <Sparkles class="w-4 h-4 mr-1.5" /> AI 智能出题
        </el-button>
        <el-button class="!w-full" @click="importDialog = true">
          <Upload class="w-4 h-4 mr-1.5" /> 导入题库
        </el-button>
        <el-button class="!w-full" @click="exportJson">
          <Download class="w-4 h-4 mr-1.5" /> 导出 JSON
        </el-button>
      </div>
    </aside>

    <div class="flex-1 min-w-0 space-y-4">
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 flex flex-wrap items-center gap-3">
        <el-select v-model="filter.chapterId" placeholder="全部章节" clearable class="!w-44" @change="onFilterChange">
          <el-option v-for="c in currentChapters" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="filter.type" placeholder="全部题型" clearable class="!w-32" @change="onFilterChange">
          <el-option label="单选题" value="SINGLE" />
          <el-option label="多选题" value="MULTIPLE" />
          <el-option label="判断题" value="JUDGE" />
          <el-option label="问答题" value="ESSAY" />
        </el-select>
        <el-input v-model="filter.keyword" placeholder="搜索题干关键词" clearable class="!w-56"
          @keyup.enter="onFilterChange" @clear="onFilterChange">
          <template #prefix><Search class="w-4 h-4 text-gray-300" /></template>
        </el-input>
        <el-button type="primary" @click="onFilterChange">查询</el-button>
        <div class="flex-1"></div>
        <el-button type="primary" plain @click="openEdit(null)">
          <Plus class="w-4 h-4 mr-1" /> 新增题目
        </el-button>
      </div>

      <!-- 批量操作条 -->
      <transition name="fade-slide">
        <div v-if="selectedIds.length"
          class="bg-indigo-50 border border-indigo-200 rounded-2xl px-4 py-3 flex flex-wrap items-center gap-3">
          <span class="text-sm text-indigo-700">已选择 <b>{{ selectedIds.length }}</b> 道题目</span>
          <div class="flex-1"></div>
          <el-button size="small" text @click="clearSelection">取消选择</el-button>
          <el-button size="small" type="danger" plain :loading="batchDeleting" @click="batchDelete">
            <Trash2 class="w-4 h-4 mr-1" /> 批量删除
          </el-button>
        </div>
      </transition>

      <div v-loading="loading" class="space-y-3 min-h-40">
        <div v-if="questions.length" class="flex items-center gap-2 px-1">
          <el-checkbox :model-value="allSelected" :indeterminate="partialSelected"
            @change="toggleSelectAll">全选本页</el-checkbox>
          <span class="text-xs text-gray-400">共 {{ total }} 道题目</span>
        </div>
        <QuestionCard v-for="q in questions" :key="q.id" :question="q" show-delete selectable
          :selected="selectedIds.includes(q.id)"
          @update:selected="v => toggleSelect(q, v)"
          @delete="removeQuestion" @toggle-favorite="toggleFavorite">
          <div class="mt-3">
            <el-button size="small" text type="primary" @click="openEdit(q.id)">编辑</el-button>
          </div>
        </QuestionCard>

        <EmptyState v-if="!questions.length && !loading"
          :title="hasFilter ? '没有符合条件的题目' : '这个科目还没有题目'"
          :description="hasFilter ? '试试清空筛选条件，或换个关键词搜索。' : '可以手动新增、按模板导入，或让 AI 帮你生成一批练习题。'">
          <template #icon><FileQuestion class="w-7 h-7" /></template>
          <template #action>
            <el-button v-if="hasFilter" size="small" @click="resetFilter">清空筛选</el-button>
            <el-button size="small" type="primary" @click="openEdit(null)">
              <Plus class="w-4 h-4 mr-1" /> 新增题目
            </el-button>
            <el-button size="small" @click="importDialog = true">
              <Upload class="w-4 h-4 mr-1" /> 导入题库
            </el-button>
            <el-button size="small" plain @click="aiDialogOpen">
              <Sparkles class="w-4 h-4 mr-1" /> AI 出题
            </el-button>
          </template>
        </EmptyState>
      </div>

      <div class="flex justify-center" v-if="total > filter.size">
        <el-pagination background layout="prev, pager, next, total" :total="total"
          :page-size="filter.size" :current-page="filter.page" @current-change="p => { filter.page = p; load() }" />
      </div>
    </div>
  </div>

  <ImportDialog v-model="importDialog" @imported="onImported" />
  <AiGenerateDialog ref="aiDialog" @imported="onImported" />
  <el-dialog v-model="editDialog" :title="editForm.id ? '编辑题目' : '新增题目'" width="680px" :close-on-click-modal="false">
    <el-form label-width="90px" label-position="left">
      <div class="grid grid-cols-2 gap-x-4">
        <el-form-item label="科目" required>
          <el-select v-model="editForm.subjectId" class="w-full" @change="editForm.chapterId = null">
            <el-option v-for="s in store.subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="editForm.chapterId" class="w-full" clearable>
            <el-option v-for="c in chaptersOf(editForm.subjectId)" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型" required>
          <el-select v-model="editForm.type" class="w-full" @change="onTypeChange">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="问答题" value="ESSAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="editForm.difficulty" :max="5" />
        </el-form-item>
      </div>
      <el-form-item label="题干" required>
        <el-input v-model="editForm.stem" type="textarea" :rows="3" placeholder="请输入题干" @input="clearError('stem')" />
        <div v-if="errors.stem" class="text-xs text-red-500 mt-1">{{ errors.stem }}</div>
      </el-form-item>
      <template v-if="isChoice">
        <el-form-item v-for="(opt, idx) in editForm.optionList" :key="idx" :label="'选项 ' + opt.key">
          <div class="flex gap-2 w-full">
            <el-input v-model="opt.content" :placeholder="'选项 ' + opt.key + ' 的内容'" @input="clearError('options')" />
            <el-button v-if="editForm.optionList.length > 2" text type="danger" @click="editForm.optionList.splice(idx, 1)">
              <X class="w-4 h-4" />
            </el-button>
          </div>
        </el-form-item>
        <div v-if="errors.options" class="text-xs text-red-500 ml-24 -mt-1 mb-2">{{ errors.options }}</div>
        <el-form-item label-width="90px">
          <el-button text type="primary" @click="addOption" :disabled="editForm.optionList.length >= 6">+ 添加选项</el-button>
          <span v-if="editForm.optionList.length >= 6" class="text-xs text-gray-400 ml-2">最多 6 个选项</span>
        </el-form-item>
        <el-form-item :label="editForm.type === 'MULTIPLE' ? '答案(多选)' : '答案'" required>
          <el-checkbox-group v-if="editForm.type === 'MULTIPLE'" v-model="editForm.multiAnswer" @change="clearError('answer')">
            <el-checkbox v-for="opt in editForm.optionList" :key="opt.key" :value="opt.key">{{ opt.key }}</el-checkbox>
          </el-checkbox-group>
          <el-radio-group v-else v-model="editForm.answer" @change="clearError('answer')">
            <el-radio v-for="opt in editForm.optionList" :key="opt.key" :value="opt.key">{{ opt.key }}</el-radio>
          </el-radio-group>
          <div v-if="errors.answer" class="text-xs text-red-500 mt-1">{{ errors.answer }}</div>
        </el-form-item>
      </template>
      <el-form-item v-if="editForm.type === 'JUDGE'" label="答案" required>
        <el-radio-group v-model="editForm.answer" @change="clearError('answer')">
          <el-radio value="TRUE">正确</el-radio>
          <el-radio value="FALSE">错误</el-radio>
        </el-radio-group>
        <div v-if="errors.answer" class="text-xs text-red-500 mt-1">{{ errors.answer }}</div>
      </el-form-item>
      <el-form-item v-if="editForm.type === 'ESSAY'" label="参考答案" required>
        <el-input v-model="editForm.answer" type="textarea" :rows="4" placeholder="填写参考答案要点" @input="clearError('answer')" />
        <div v-if="errors.answer" class="text-xs text-red-500 mt-1">{{ errors.answer }}</div>
      </el-form-item>
      <el-form-item label="解析">
        <el-input v-model="editForm.analysis" type="textarea" :rows="2" placeholder="选填，答错时展示给考生" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveQuestion">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="subjectDialog" title="新建科目" width="420px">
    <el-form label-width="70px">
      <el-form-item label="名称" required>
        <el-input v-model="newSubject.name" placeholder="如：软件设计师" @input="clearError('subjectName')" />
        <div v-if="errors.subjectName" class="text-xs text-red-500 mt-1">{{ errors.subjectName }}</div>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="newSubject.description" type="textarea" :rows="2" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="subjectDialog = false">取消</el-button>
      <el-button type="primary" :loading="savingSubject" @click="saveSubject">创建</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="chapterDialog" title="添加章节" width="420px">
    <el-input v-model="newChapterName" placeholder="章节名称，如：数据结构与算法" @input="clearError('chapterName')" />
    <div v-if="errors.chapterName" class="text-xs text-red-500 mt-1">{{ errors.chapterName }}</div>
    <template #footer>
      <el-button @click="chapterDialog = false">取消</el-button>
      <el-button type="primary" :loading="savingChapter" @click="saveChapter">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineOptions({ name: 'QuestionBank' })

import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { Plus, MoreVertical, Search, Sparkles, Upload, Download, X, Trash2, FileQuestion } from '@lucide/vue'
import api from '../api'
import { useAppStore } from '../stores/app'
import QuestionCard from '../components/QuestionCard.vue'
import ImportDialog from '../components/ImportDialog.vue'
import AiGenerateDialog from '../components/AiGenerateDialog.vue'
import EmptyState from '../components/EmptyState.vue'
import { notify, confirmAction, withLoading } from '../utils/feedback'
import { downloadFile } from '../utils/download'

const store = useAppStore()
const questions = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const savingSubject = ref(false)
const savingChapter = ref(false)
const batchDeleting = ref(false)
const aiDialog = ref(null)
const importDialog = ref(false)
const editDialog = ref(false)
const subjectDialog = ref(false)
const chapterDialog = ref(false)
const currentSubject = ref(null)
const newChapterName = ref('')
const selectedIds = ref([])
const errors = reactive({})
const newSubject = reactive({ name: '', description: '' })

const filter = reactive({ subjectId: null, chapterId: null, type: null, keyword: '', page: 1, size: 10 })
const editForm = reactive({
  id: null, subjectId: null, chapterId: null, type: 'SINGLE', stem: '',
  optionList: [], multiAnswer: [], answer: '', analysis: '', difficulty: 3
})

onMounted(async () => {
  await store.loadSubjects(true).catch(() => {})
  if (store.subjects.length) selectSubject(store.subjects[0])
})

onActivated(async () => {
  await store.loadSubjects(true).catch(() => {})
  if (filter.subjectId) {
    currentSubject.value = store.subjects.find(s => s.id === filter.subjectId) || null
    if (!currentSubject.value && store.subjects.length) selectSubject(store.subjects[0])
    else load()
  } else if (store.subjects.length) {
    selectSubject(store.subjects[0])
  }
})

const currentChapters = computed(() => currentSubject.value?.chapters || [])
const isChoice = computed(() => editForm.type === 'SINGLE' || editForm.type === 'MULTIPLE')
const hasFilter = computed(() => !!(filter.chapterId || filter.type || filter.keyword))
const selectedSet = computed(() => new Set(selectedIds.value))
const pageIds = computed(() => questions.value.map(q => q.id))
const allSelected = computed(() => pageIds.value.length > 0 && pageIds.value.every(id => selectedSet.value.has(id)))
const partialSelected = computed(() => !allSelected.value && pageIds.value.some(id => selectedSet.value.has(id)))

function chaptersOf(subjectId) {
  return store.subjects.find(s => s.id === subjectId)?.chapters || []
}

function clearError(field) {
  delete errors[field]
}
function clearAllErrors() {
  Object.keys(errors).forEach(k => delete errors[k])
}

function selectSubject(s) {
  currentSubject.value = s
  filter.subjectId = s.id
  filter.chapterId = null
  filter.page = 1
  clearSelection()
  load()
}

function onFilterChange() {
  filter.page = 1
  clearSelection()
  load()
}

function resetFilter() {
  filter.chapterId = null
  filter.type = null
  filter.keyword = ''
  onFilterChange()
}

function clearSelection() {
  selectedIds.value = []
}

function toggleSelect(q, checked) {
  if (checked) {
    if (!selectedSet.value.has(q.id)) selectedIds.value = [...selectedIds.value, q.id]
  } else {
    selectedIds.value = selectedIds.value.filter(id => id !== q.id)
  }
}

function toggleSelectAll(checked) {
  if (checked) {
    const merged = new Set(selectedIds.value)
    pageIds.value.forEach(id => merged.add(id))
    selectedIds.value = [...merged]
  } else {
    const page = new Set(pageIds.value)
    selectedIds.value = selectedIds.value.filter(id => !page.has(id))
  }
}

async function load() {
  if (!filter.subjectId) return
  await withLoading(loading, async () => {
    const res = await api.pageQuestions({
      subjectId: filter.subjectId, chapterId: filter.chapterId || undefined,
      type: filter.type || undefined, keyword: filter.keyword || undefined,
      page: filter.page, size: filter.size
    })
    questions.value = res.content
    total.value = res.total
  })
}

function openEdit(id) {
  editForm.id = id
  clearAllErrors()
  if (id) {
    loading.value = true
    api.getQuestion(id).then(d => {
      editForm.subjectId = d.subjectId
      editForm.chapterId = d.chapterId
      editForm.type = d.type
      editForm.stem = d.stem
      editForm.optionList = Object.entries(d.options || {}).map(([key, content]) => ({ key, content }))
      editForm.multiAnswer = d.type === 'MULTIPLE' ? d.answer.split('') : []
      editForm.answer = d.type === 'MULTIPLE' ? '' : d.answer
      editForm.analysis = d.analysis || ''
      editForm.difficulty = d.difficulty || 3
      editDialog.value = true
    }).catch(() => {}).finally(() => { loading.value = false })
  } else {
    editForm.subjectId = filter.subjectId
    editForm.chapterId = filter.chapterId
    editForm.type = 'SINGLE'
    editForm.stem = ''
    editForm.optionList = [
      { key: 'A', content: '' }, { key: 'B', content: '' },
      { key: 'C', content: '' }, { key: 'D', content: '' }
    ]
    editForm.multiAnswer = []
    editForm.answer = ''
    editForm.analysis = ''
    editForm.difficulty = 3
    editDialog.value = true
  }
}

function onTypeChange() {
  editForm.answer = ''
  editForm.multiAnswer = []
  clearError('answer')
  if (isChoice.value && editForm.optionList.length < 2) {
    editForm.optionList = [{ key: 'A', content: '' }, { key: 'B', content: '' }]
  }
}

function addOption() {
  const keys = 'ABCDEF'
  editForm.optionList.push({ key: keys[editForm.optionList.length], content: '' })
}

/** 表单校验：一次性收集所有问题并内联展示，而不是只弹一个提示 */
function validate() {
  clearAllErrors()
  let firstError = ''
  const setError = (field, msg) => {
    if (!errors[field]) { errors[field] = msg; if (!firstError) firstError = msg }
  }

  if (!editForm.subjectId) setError('stem', '请选择题库科目')
  if (!editForm.stem.trim()) setError('stem', '请填写题干')

  if (isChoice.value) {
    if (editForm.optionList.some(o => !o.content.trim())) setError('options', '选项内容不能为空')
    if (editForm.optionList.length < 2) setError('options', '选择题至少需要两个选项')
  }

  const answer = editForm.type === 'MULTIPLE' ? [...editForm.multiAnswer].sort().join('') : editForm.answer
  if (isChoice.value && editForm.type === 'MULTIPLE' && editForm.multiAnswer.length < 2) {
    setError('answer', '多选题请至少选择两个答案')
  } else if (!answer || !String(answer).trim()) {
    setError('answer', editForm.type === 'ESSAY' ? '请填写参考答案' : '请设置答案')
  }

  return firstError
}

async function saveQuestion() {
  const firstError = validate()
  if (firstError) return notify.warning(firstError)

  const answer = editForm.type === 'MULTIPLE' ? [...editForm.multiAnswer].sort().join('') : editForm.answer
  const options = {}
  if (isChoice.value) {
    for (const opt of editForm.optionList) options[opt.key] = opt.content
  }
  const payload = {
    subjectId: editForm.subjectId, chapterId: editForm.chapterId, type: editForm.type,
    stem: editForm.stem, options, answer, analysis: editForm.analysis,
    difficulty: editForm.difficulty, source: '手动录入'
  }

  await withLoading(saving, async () => {
    if (editForm.id) await api.updateQuestion(editForm.id, payload)
    else await api.createQuestion(payload)
    notify.success(editForm.id ? '题目已更新' : '题目已新增')
    editDialog.value = false
    await store.loadSubjects(true)
    await load()
  })
}

async function removeQuestion(q) {
  try {
    await confirmAction(`确定删除这道题目吗？删除后无法恢复。`, { title: '删除题目' })
  } catch {
    return
  }
  await api.deleteQuestion(q.id)
  notify.success('题目已删除')
  selectedIds.value = selectedIds.value.filter(id => id !== q.id)
  await store.loadSubjects(true)
  await load()
}

async function batchDelete() {
  const count = selectedIds.value.length
  if (!count) return
  try {
    await confirmAction(`确定删除选中的 ${count} 道题目吗？删除后无法恢复。`, { title: '批量删除' })
  } catch {
    return
  }
  await withLoading(batchDeleting, () => api.batchDeleteQuestions(selectedIds.value), {
    success: `已删除 ${count} 道题目`
  })
  clearSelection()
  await store.loadSubjects(true)
  await load()
}

async function toggleFavorite(q) {
  try {
    if (q.favorite) {
      await api.removeFavorite(q.id)
      q.favorite = false
    } else {
      await api.addFavorite(q.id)
      q.favorite = true
      notify.success('已收藏，可在收藏夹中查看')
    }
  } catch {
    // 拦截器已给出提示
  }
}

async function saveSubject() {
  clearError('subjectName')
  if (!newSubject.name.trim()) {
    errors.subjectName = '请填写科目名称'
    return notify.warning('请填写科目名称')
  }
  await withLoading(savingSubject, () => api.createSubject(newSubject), { success: '科目已创建' })
  subjectDialog.value = false
  newSubject.name = ''
  newSubject.description = ''
  await store.loadSubjects(true)
}

async function subjectCommand(cmd, s) {
  if (cmd === 'rename') {
    const { ElMessageBox } = await import('element-plus')
    ElMessageBox.prompt('新的科目名称', '重命名', {
      inputValue: s.name,
      inputValidator: v => (v && v.trim() ? true : '科目名称不能为空')
    }).then(async ({ value }) => {
      await api.updateSubject(s.id, { name: value.trim() })
      notify.success('已重命名')
      await store.loadSubjects(true)
      if (filter.subjectId === s.id) {
        currentSubject.value = store.subjects.find(x => x.id === s.id) || null
      }
    }).catch(() => {})
  } else if (cmd === 'addChapter') {
    currentSubject.value = s
    newChapterName.value = ''
    clearError('chapterName')
    chapterDialog.value = true
  } else if (cmd === 'delete') {
    try {
      await confirmAction(`确定删除科目「${s.name}」吗？科目下不能有题目。`, { title: '删除科目' })
    } catch {
      return
    }
    try {
      await api.deleteSubject(s.id)
      notify.success('科目已删除')
    } catch {
      return
    }
    await store.loadSubjects(true)
    if (filter.subjectId === s.id) {
      if (store.subjects.length) selectSubject(store.subjects[0])
      else { questions.value = []; total.value = 0; filter.subjectId = null; currentSubject.value = null }
    }
  }
}

async function saveChapter() {
  clearError('chapterName')
  const name = newChapterName.value.trim()
  if (!name) {
    errors.chapterName = '请填写章节名称'
    return notify.warning('请填写章节名称')
  }
  await withLoading(savingChapter, () => api.createChapter(currentSubject.value.id, { name }), {
    success: '章节已创建'
  })
  chapterDialog.value = false
  await store.loadSubjects(true)
  currentSubject.value = store.subjects.find(s => s.id === currentSubject.value?.id) || null
}

function aiDialogOpen() {
  aiDialog.value?.open()
}

async function onImported() {
  await store.loadSubjects(true)
  clearSelection()
  if (store.subjects.length && !filter.subjectId) selectSubject(store.subjects[0])
  else await load()
}

async function exportJson() {
  const url = api.exportUrl + (filter.subjectId ? `?subjectId=${filter.subjectId}` : '')
  try {
    await downloadFile(url, 'ruankao-questions.json')
    notify.success('题库已导出')
  } catch (e) {
    notify.error(e.message || '导出失败')
  }
}
</script>
