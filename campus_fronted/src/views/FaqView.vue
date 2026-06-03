<template>
  <div class="faq-page">
    <!-- ── 页面头部 ── -->
    <div class="page-header">
      <div>
        <h2 class="page-title">FAQ管理</h2>
        <p class="page-subtitle">管理校园高频问题与标准答案，提升智能助理回答质量与命中率。</p>
      </div>
    </div>

    <!-- ── 统计卡片 ── -->
    <div class="stat-strip">
      <div class="stat-card">
        <div class="stat-icon-box" style="background:#e6f0ff">
          <el-icon :size="22" color="#1677ff"><DocumentCopy /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">FAQ总数</div>
          <div class="stat-value">{{ totalCount.toLocaleString() }}</div>
          <div class="stat-trend up">↑ 28 <span class="trend-week">较上周</span></div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box" style="background:#f0fdf4">
          <el-icon :size="22" color="#17a855"><CircleCheck /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">启用中</div>
          <div class="stat-value">{{ enabledCount.toLocaleString() }}</div>
          <div class="stat-rate">占比 {{ enabledRate }}%</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box" style="background:#f5f0ff">
          <el-icon :size="22" color="#7b5ea7"><DataAnalysis /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">本周命中次数</div>
          <div class="stat-value">{{ totalHits.toLocaleString() }}</div>
          <div class="stat-trend up">↑ 2,341 <span class="trend-week">较上周</span></div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box" style="background:#fff7ed">
          <el-icon :size="22" color="#fa8c16"><Warning /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">待补 FAQ 推荐</div>
          <div class="stat-value">{{ suggestions.length }}</div>
          <div class="stat-rate">近 {{ suggestDays }} 天未命中</div>
        </div>
      </div>
    </div>

    <!-- ── 主体：表格 + 右侧面板 ── -->
    <div class="faq-body" :class="{ 'with-panel': panelVisible }">

      <!-- 左侧主内容 -->
      <div class="faq-main">

        <!-- 筛选栏 -->
        <div class="filter-bar">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索问题或关键词"
            clearable
            class="filter-search"
            @keyup.enter="doSearch"
          >
            <template #suffix>
              <el-icon style="cursor:pointer;color:#c2c7d0" @click="doSearch"><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="filters.category" placeholder="全部分类" clearable class="filter-sel">
            <el-option label="全部分类" value="" />
            <el-option v-for="c in categories" :key="c" :label="categoryLabel(c)" :value="c" />
          </el-select>
          <el-select v-model="filters.enabled" placeholder="全部状态" clearable class="filter-sel">
            <el-option label="全部状态" value="" />
            <el-option label="已启用" :value="1" />
            <el-option label="已禁用" :value="0" />
          </el-select>
          <el-select v-model="filters.priority" placeholder="全部优先级" clearable class="filter-sel">
            <el-option label="全部优先级" value="" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
          <el-button type="primary" @click="doSearch" :loading="loading">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>

        <!-- 操作栏 -->
        <div class="action-bar">
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon>&nbsp;新增FAQ
          </el-button>
          <el-button @click="triggerImport">
            <el-icon><Upload /></el-icon>&nbsp;批量导入
          </el-button>
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>&nbsp;导出<span v-if="selectedFaqs.length">({{ selectedFaqs.length }})</span>
          </el-button>
          <input ref="importRef" type="file" accept=".json,.csv,application/json,text/csv" hidden @change="handleImport" />
        </div>

        <!-- 表格 -->
        <div class="table-wrap">
          <el-table
            :data="faqs"
            v-loading="loading"
            border
            :row-class-name="rowClassName"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="44" />
            <el-table-column prop="question" label="问题" min-width="360" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="question-link" @click="openEdit(row)">{{ row.question }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="140">
              <template #default="{ row }">
                <el-tag v-if="row.category" size="small" type="info" effect="plain">{{ categoryLabel(row.category) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="86" align="center">
              <template #default="{ row }">
                <span class="p-badge" :class="`p-${(row.priority || 'medium').toLowerCase()}`">
                  {{ priorityLabel(row.priority) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="启用状态" width="104" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled === 1"
                  @change="toggleFaq(row)"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column prop="hitCount" label="命中次数" width="112" align="center" sortable>
              <template #default="{ row }">{{ (row.hitCount || 0).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="174" sortable show-overflow-tooltip>
              <template #default="{ row }">
                <span class="time-text">{{ formatTime(row.updatedAt || row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="132" align="center" fixed="right">
              <template #default="{ row }">
                <div class="op-actions">
                  <el-tooltip content="编辑" placement="top">
                    <el-button text size="small" @click="openEdit(row)">
                      <el-icon :size="15"><Edit /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="复制" placement="top">
                    <el-button text size="small" @click="copyFaq(row)">
                      <el-icon :size="15"><CopyDocument /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-dropdown trigger="click" @command="cmd => handleCmd(cmd, row)">
                    <el-button text size="small">
                      <el-icon :size="15"><MoreFilled /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="delete" style="color:#ff4d4f">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 待补 FAQ 推荐（来自对话日志） -->
        <div class="suggest-section">
          <div class="suggest-hd">
            <div>
              <h3 class="suggest-title">待补 FAQ 推荐</h3>
              <p class="suggest-sub">从学生高频提问中挖掘未命中 FAQ 的问题，一键采纳后请人工校对答案。</p>
            </div>
            <div class="suggest-actions">
              <el-select v-model="suggestDays" size="small" class="suggest-days" @change="loadSuggestions">
                <el-option :value="7" label="近 7 天" />
                <el-option :value="14" label="近 14 天" />
                <el-option :value="30" label="近 30 天" />
              </el-select>
              <el-button size="small" :loading="suggestLoading" @click="loadSuggestions">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
            </div>
          </div>
          <div v-loading="suggestLoading" class="suggest-list">
            <div v-if="!suggestLoading && suggestions.length === 0" class="suggest-empty">
              暂无推荐。请让学生多问几轮政策类问题后再查看。
            </div>
            <div v-for="(s, i) in suggestions" :key="i" class="suggest-card">
              <div class="suggest-card-top">
                <span class="suggest-rank">{{ i + 1 }}</span>
                <span class="suggest-q">{{ s.question }}</span>
                <el-tag size="small" effect="plain" type="warning">{{ s.reason }}</el-tag>
              </div>
              <div class="suggest-meta">
                <span>提问 {{ s.askCount }} 次</span>
                <span v-if="s.avgMs">均耗时 {{ s.avgMs }} ms</span>
                <span v-if="s.intent">{{ intentLabel(s.intent) }}</span>
                <span v-if="s.lastAskedAt">最近 {{ formatTime(s.lastAskedAt) }}</span>
              </div>
              <div v-if="s.sampleAnswer" class="suggest-answer">{{ s.sampleAnswer }}</div>
              <div class="suggest-card-ft">
                <el-button size="small" type="primary" plain @click="adoptSuggestion(s)">采纳为 FAQ</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="pager-bar">
          <span class="pager-total">共 {{ total }} 条</span>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="prev, pager, next, sizes, jumper"
            background
            small
            @change="loadFaqs"
          />
        </div>
      </div>

      <!-- 右侧编辑面板 -->
      <transition name="panel-slide">
        <div v-if="panelVisible" class="edit-panel">

          <!-- 面板头 -->
          <div class="ep-header">
            <span class="ep-title">{{ editTarget ? '编辑FAQ' : '新增FAQ' }}</span>
            <el-icon class="ep-close" @click="closePanel"><Close /></el-icon>
          </div>

          <!-- 表单区 -->
          <div class="ep-body">
            <el-form :model="form" label-position="top" size="small" class="ep-form">

              <el-form-item>
                <template #label>
                  <div class="ep-label-row">
                    <span>问题 <em class="req-star">*</em></span>
                    <span class="ep-count">{{ form.question.length }}/100</span>
                  </div>
                </template>
                <el-input
                  v-model="form.question"
                  :maxlength="100"
                  show-word-limit
                  placeholder="输入用户可能提问的问题..."
                />
              </el-form-item>

              <el-form-item>
                <template #label>
                  <div class="ep-label-row">
                    <span>标准答案 <em class="req-star">*</em></span>
                    <span class="ep-count">{{ form.answer.length }}/2000</span>
                  </div>
                </template>
                <el-input
                  v-model="form.answer"
                  type="textarea"
                  :rows="5"
                  :maxlength="2000"
                  resize="none"
                  placeholder="输入标准回答内容..."
                />
              </el-form-item>

              <el-form-item label="分类">
                <el-select v-model="form.category" style="width:100%" placeholder="请选择分类" clearable>
                  <el-option v-for="c in categories" :key="c" :label="categoryLabel(c)" :value="c" />
                </el-select>
              </el-form-item>

              <el-form-item label="关键词">
                <div class="kw-area">
                  <el-tag
                    v-for="(kw, i) in keywordTags"
                    :key="i"
                    closable
                    size="small"
                    class="kw-tag"
                    @close="keywordTags.splice(i, 1)"
                  >{{ kw }}</el-tag>
                  <el-input
                    v-if="kwInputShow"
                    ref="kwInputRef"
                    v-model="kwInputVal"
                    size="small"
                    class="kw-new-input"
                    placeholder="回车确认"
                    @keyup.enter="confirmKw"
                    @blur="confirmKw"
                  />
                  <el-button v-else size="small" text type="primary" @click="showKwInput">
                    <el-icon><Plus /></el-icon> 添加关键词
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item label="引用来源">
                <div class="src-area">
                  <div v-for="(s, i) in sources" :key="i" class="src-item">
                    <div class="src-icon-box">
                      <el-icon :size="13" color="#1677ff"><Document /></el-icon>
                    </div>
                    <div class="src-text">
                      <div class="src-name">{{ s.name }}</div>
                      <div class="src-meta">{{ s.meta }}</div>
                    </div>
                    <el-icon class="src-del" @click="sources.splice(i, 1)"><Close /></el-icon>
                  </div>
                  <el-button size="small" text type="primary" @click="addSource">
                    <el-icon><Plus /></el-icon> 添加引用来源
                  </el-button>
                </div>
              </el-form-item>

            </el-form>
          </div>

          <!-- 底部操作 -->
          <div class="ep-footer">
            <el-button size="small" :icon="View" plain @click="handlePreview">预览</el-button>
            <div class="ep-footer-r">
              <el-button size="small" @click="saveForm" :loading="saving">保存</el-button>
              <el-button size="small" type="primary" @click="publishForm" :loading="publishing">发布</el-button>
            </div>
          </div>

          <!-- 高频已命中 FAQ -->
          <div class="hot-box">
            <div class="hot-hd">
              <span class="hot-title">已命中 FAQ 排行</span>
              <el-button size="small" text type="primary" @click="loadTopFaqs">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
            </div>
            <div v-if="topFaqs.length === 0" class="hot-empty">暂无数据</div>
            <div v-for="(f, i) in topFaqs.slice(0, 5)" :key="f.id" class="hot-row">
              <span class="hot-rank" :class="{ fire: i < 2 }">{{ i < 2 ? '🔥' : '' }}{{ i + 1 }}</span>
              <span class="hot-q" @click="openEdit(f)">{{ f.question }}</span>
              <span class="hot-cnt">命中 {{ (f.hitCount || 0).toLocaleString() }} 次</span>
            </div>
          </div>

        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Edit, Search, Refresh, Upload, Download,
  DocumentCopy, CircleCheck, DataAnalysis, Warning,
  CopyDocument, MoreFilled, Close, View, Document
} from '@element-plus/icons-vue'
import { faqApi } from '../api/faq'

/* ── state ── */
const faqs       = ref([])
const topFaqs    = ref([])
const suggestions = ref([])
const suggestDays = ref(7)
const suggestLoading = ref(false)
const categories = ref([])
const selectedFaqs = ref([])
const loading    = ref(false)
const saving     = ref(false)
const publishing = ref(false)
const importRef  = ref(null)
const total      = ref(0)
const page       = ref(1)
const pageSize   = ref(10)

const filters = reactive({ keyword: '', category: '', enabled: '', priority: '' })

const panelVisible = ref(false)
const editTarget   = ref(null)
const form         = reactive({ question: '', answer: '', category: '', priority: 'MEDIUM' })

const keywordTags = ref([])
const sources     = ref([])
const kwInputShow = ref(false)
const kwInputVal  = ref('')
const kwInputRef  = ref(null)

/* ── computed stats ── */
const totalCount  = computed(() => total.value || faqs.value.length)
const enabledCount = computed(() => faqs.value.filter(f => f.enabled === 1).length)
const enabledRate = computed(() => {
  if (!faqs.value.length) return '0.00'
  return ((enabledCount.value / faqs.value.length) * 100).toFixed(2)
})
const totalHits   = computed(() => faqs.value.reduce((s, f) => s + (f.hitCount || 0), 0))
const zeroHitCount = computed(() => faqs.value.filter(f => !f.hitCount).length)

/* ── lifecycle ── */
onMounted(() => {
  loadFaqs()
  loadTopFaqs()
  loadCategories()
  loadSuggestions()
})

/* ── API ── */
async function loadSuggestions() {
  suggestLoading.value = true
  try {
    const res = await faqApi.suggestions({ days: suggestDays.value, minCount: 1, limit: 15 })
    suggestions.value = res.data || []
  } catch {
    suggestions.value = []
  } finally {
    suggestLoading.value = false
  }
}

function adoptSuggestion(s) {
  openCreate()
  form.question = s.question || ''
  form.answer = s.sampleAnswer || ''
  form.category = guessCategory(s.intent)
  form.priority = s.askCount >= 3 ? 'HIGH' : 'MEDIUM'
  ElMessage.info('已填入推荐内容，请校对后发布')
}

function guessCategory(intent) {
  if (intent === 'ACADEMIC_TOOL') return 'academic'
  return 'academic'
}

function intentLabel(intent) {
  return {
    POLICY_QA: '政策问答',
    DOC_SEARCH: '文档检索',
    ACADEMIC_TOOL: '教务工具',
  }[intent] || intent
}

async function loadFaqs() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (filters.keyword)      params.keyword  = filters.keyword
    if (filters.category)     params.category = filters.category
    if (filters.enabled !== '') params.enabled = filters.enabled
    if (filters.priority)     params.priority = filters.priority
    const res = await faqApi.list(params)
    faqs.value  = res.data  || []
    total.value = res.total || faqs.value.length
  } finally {
    loading.value = false
  }
}

async function loadTopFaqs() {
  try {
    const res = await faqApi.top(10)
    topFaqs.value = res.data || []
  } catch {}
}

async function loadCategories() {
  try {
    const res = await faqApi.categories()
    categories.value = res.data || []
  } catch {}
}

/* ── filter ── */
function doSearch() {
  page.value = 1
  loadFaqs()
}

function resetFilters() {
  Object.assign(filters, { keyword: '', category: '', enabled: '', priority: '' })
  page.value = 1
  loadFaqs()
}

/* ── panel ── */
function openCreate() {
  editTarget.value = null
  Object.assign(form, { question: '', answer: '', category: '', priority: 'MEDIUM' })
  keywordTags.value = []
  sources.value = []
  panelVisible.value = true
}

function openEdit(row) {
  editTarget.value = row
  Object.assign(form, {
    question: row.question || '',
    answer:   row.answer   || '',
    category: row.category || '',
    priority: row.priority || 'MEDIUM',
  })
  keywordTags.value = row.keywords ? row.keywords.split(',').map(k => k.trim()).filter(Boolean) : []
  sources.value = []
  panelVisible.value = true
}

function closePanel() {
  panelVisible.value = false
  editTarget.value   = null
}

/* ── keywords ── */
function showKwInput() {
  kwInputShow.value = true
  nextTick(() => kwInputRef.value?.focus())
}

function confirmKw() {
  const v = kwInputVal.value.trim()
  if (v && !keywordTags.value.includes(v)) keywordTags.value.push(v)
  kwInputVal.value  = ''
  kwInputShow.value = false
}

/* ── sources ── */
function addSource() {
  sources.value.push({ name: '未命名文档.pdf', meta: '请补充来源信息' })
}

/* ── save / publish ── */
async function saveForm() {
  if (!form.question.trim() || !form.answer.trim()) {
    ElMessage.warning('问题和答案不能为空')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, keywords: keywordTags.value.join(','), enabled: editTarget.value?.enabled ?? 1 }
    if (editTarget.value) {
      await faqApi.update(editTarget.value.id, payload)
      ElMessage.success('已保存')
    } else {
      await faqApi.create(payload)
      ElMessage.success('已创建')
    }
    closePanel()
    loadFaqs()
    loadCategories()
    loadSuggestions()
  } finally {
    saving.value = false
  }
}

async function publishForm() {
  if (!form.question.trim() || !form.answer.trim()) {
    ElMessage.warning('问题和答案不能为空')
    return
  }
  publishing.value = true
  try {
    const payload = { ...form, keywords: keywordTags.value.join(','), enabled: 1 }
    if (editTarget.value) {
      await faqApi.update(editTarget.value.id, payload)
    } else {
      await faqApi.create(payload)
    }
    ElMessage.success('已发布')
    closePanel()
    loadFaqs()
    loadCategories()
    loadSuggestions()
  } finally {
    publishing.value = false
  }
}

/* ── table actions ── */
async function toggleFaq(row) {
  try {
    await faqApi.toggle(row.id)
    row.enabled = row.enabled === 1 ? 0 : 1
  } catch {}
}

async function deleteFaq(id) {
  try {
    await ElMessageBox.confirm('确认删除该 FAQ？', '删除确认', { type: 'warning' })
    await faqApi.remove(id)
    ElMessage.success('已删除')
    if (editTarget.value?.id === id) closePanel()
    loadFaqs()
    loadTopFaqs()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function copyFaq(row) {
  openCreate()
  Object.assign(form, {
    question: row.question + '（副本）',
    answer:   row.answer,
    category: row.category || '',
    priority: row.priority || 'MEDIUM',
  })
  keywordTags.value = row.keywords ? row.keywords.split(',').map(k => k.trim()).filter(Boolean) : []
}

function handleCmd(cmd, row) {
  if (cmd === 'delete') deleteFaq(row.id)
}

function handleSelectionChange(rows) {
  selectedFaqs.value = rows || []
}

function rowClassName({ row }) {
  return editTarget.value?.id === row.id ? 'row-editing' : ''
}

function handlePreview() {
  ElMessage.info('预览功能开发中')
}

/* ── import / export ── */
function triggerImport() { importRef.value.click() }

async function handleImport(e) {
  const file = e.target.files[0]
  if (!file) return
  try {
    const text = await file.text()
    const data = parseImportFile(text, file.name)
    if (!data.length) { ElMessage.warning('没有可导入的数据'); return }
    await ElMessageBox.confirm(`确认导入 ${data.length} 条 FAQ？`, '导入确认', { type: 'warning' })
    await faqApi.batchImport(data)
    ElMessage.success(`已导入 ${data.length} 条 FAQ`)
    loadFaqs(); loadTopFaqs(); loadCategories()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('导入失败：' + (e.message || '格式错误'))
  } finally { e.target.value = '' }
}

async function handleExport() {
  try {
    if (!selectedFaqs.value.length) {
      ElMessage.warning('请先勾选要导出的 FAQ')
      return
    }
    const data = selectedFaqs.value.map(toExportRow)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href = url; a.download = `faq-selected-${new Date().toISOString().slice(0, 10)}.json`; a.click()
    URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

/* ── helpers ── */
const categoryLabelMap = {
  academic: '学业咨询',
  campus_service: '校园服务',
  scholarship: '奖助学金',
  library: '图书馆',
  logistics: '后勤服务',
}
const categoryCodeMap = Object.fromEntries(Object.entries(categoryLabelMap).map(([code, label]) => [label, code]))

function categoryLabel(category) {
  return categoryLabelMap[category] || String(category || '').replace(/_/g, ' ')
}

function categoryCode(category) {
  return categoryCodeMap[category] || category
}

function priorityLabel(p) { return { HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || '中' }

function priorityCode(priority) {
  return { 高: 'HIGH', 中: 'MEDIUM', 低: 'LOW', HIGH: 'HIGH', MEDIUM: 'MEDIUM', LOW: 'LOW' }[priority] || 'MEDIUM'
}

function enabledCode(enabled) {
  if (enabled === 0 || enabled === false || enabled === '0' || enabled === '否' || enabled === '禁用' || enabled === '已禁用') return 0
  return 1
}

function toExportRow(row) {
  return {
    question: row.question || '',
    answer: row.answer || '',
    category: row.category || '',
    categoryLabel: categoryLabel(row.category),
    keywords: row.keywords || '',
    priority: row.priority || 'MEDIUM',
    priorityLabel: priorityLabel(row.priority),
    enabled: row.enabled ?? 1,
    hitCount: row.hitCount || 0,
  }
}

function normalizeImportRow(row, index) {
  const question = String(row.question ?? row['问题'] ?? '').trim()
  const answer = String(row.answer ?? row['答案'] ?? row['标准答案'] ?? '').trim()
  if (!question || !answer) throw new Error(`第 ${index + 1} 行缺少问题或答案`)
  return {
    question,
    answer,
    category: categoryCode(String(row.category ?? row.categoryLabel ?? row['分类'] ?? '').trim()),
    keywords: String(row.keywords ?? row['关键词'] ?? '').trim(),
    priority: priorityCode(String(row.priority ?? row.priorityLabel ?? row['优先级'] ?? 'MEDIUM').trim()),
    enabled: enabledCode(row.enabled ?? row['启用状态'] ?? row['是否启用'] ?? 1),
  }
}

function parseImportFile(text, filename) {
  const isCsv = filename.toLowerCase().endsWith('.csv')
  const rows = isCsv ? parseCsv(text) : JSON.parse(text)
  if (!Array.isArray(rows)) throw new Error(isCsv ? 'CSV 内容为空' : 'JSON 需为数组')
  return rows.map(normalizeImportRow)
}

function parseCsv(text) {
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.trim())
  if (lines.length < 2) return []
  const headers = splitCsvLine(lines[0]).map(h => h.trim())
  return lines.slice(1).map(line => {
    const values = splitCsvLine(line)
    return Object.fromEntries(headers.map((h, i) => [h, values[i] ?? '']))
  })
}

function splitCsvLine(line) {
  const values = []
  let current = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"' && line[i + 1] === '"') {
      current += '"'
      i++
    } else if (ch === '"') {
      inQuotes = !inQuotes
    } else if (ch === ',' && !inQuotes) {
      values.push(current)
      current = ''
    } else {
      current += ch
    }
  }
  values.push(current)
  return values.map(v => v.trim())
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
/* ── 页面容器 ── */
.faq-page {
  height: 100%;
  overflow-y: auto;
  background: #f5f7fa;
  padding: 20px 24px;
  box-sizing: border-box;
}

.page-header { margin-bottom: 16px; }
.page-title    { margin: 0 0 4px; font-size: 20px; font-weight: 700; color: #1d2129; }
.page-subtitle { margin: 0; font-size: 13px; color: #86909c; line-height: 1.5; }

/* ── 统计条 ── */
.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.stat-icon-box {
  width: 48px; height: 48px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.stat-body { flex: 1; min-width: 0; }
.stat-label { font-size: 12px; color: #86909c; margin-bottom: 2px; }
.stat-value { font-size: 26px; font-weight: 700; color: #1d2129; line-height: 1.2; }
.stat-trend { font-size: 12px; margin-top: 4px; display: flex; align-items: center; gap: 3px; }
.stat-trend.up   { color: #17a855; }
.stat-trend.down { color: #ff4d4f; }
.trend-week { color: #86909c; margin-left: 2px; }
.stat-rate { font-size: 12px; color: #17a855; margin-top: 4px; }

/* ── 主体布局 ── */
.faq-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.faq-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── 筛选栏 ── */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  flex-wrap: wrap;
}
.filter-search { width: 220px; }
.filter-sel    { width: 130px; }

/* ── 操作栏 ── */
.action-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ── 表格 ── */
.table-wrap {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #f0f2f5;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.table-wrap :deep(.el-table) {
  width: 100%;
}
.question-link {
  color: #1d2129;
  cursor: pointer;
  font-size: 13px;
}
.question-link:hover { color: #1677ff; }

.op-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  white-space: nowrap;
}
.op-actions :deep(.el-button) {
  margin-left: 0;
  padding: 4px 5px;
}

.p-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}
.p-high   { background: #fff1f0; color: #ff4d4f; border-color: #ffccc7; }
.p-medium { background: #fff7e6; color: #fa8c16; border-color: #ffd591; }
.p-low    { background: #f6ffed; color: #52c41a; border-color: #b7eb8f; }

.time-text { font-size: 12px; color: #86909c; }

.table-wrap :deep(.row-editing) { background: #f0f5ff !important; }
.table-wrap :deep(.el-table__header-wrapper th) {
  background: #f8fafc;
  color: #4e5969;
  font-size: 13px;
  font-weight: 500;
}

/* ── 分页 ── */
.pager-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0 4px;
}
.pager-total { font-size: 13px; color: #86909c; white-space: nowrap; }

/* ── 编辑面板 ── */
.edit-panel {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ep-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 10px 10px 0 0;
  padding: 12px 16px;
  border-bottom: none;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.ep-title { font-size: 14px; font-weight: 600; color: #1d2129; }
.ep-close {
  font-size: 16px;
  color: #86909c;
  cursor: pointer;
  transition: color .15s;
}
.ep-close:hover { color: #1d2129; }

.ep-body {
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 0;
  padding: 0 16px 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}

.ep-form :deep(.el-form-item) { margin-bottom: 14px; }
.ep-form :deep(.el-form-item__label) { padding-bottom: 4px; line-height: 1.4; }

.ep-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  font-size: 13px;
  color: #1d2129;
  font-weight: 500;
}
.req-star { color: #ff4d4f; font-style: normal; margin-left: 2px; }
.ep-count { font-size: 11px; color: #c2c7d0; font-weight: 400; }

/* 关键词区 */
.kw-area {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-height: 28px;
}
.kw-tag { border-radius: 4px; }
.kw-new-input { width: 90px; }

/* 来源区 */
.src-area { display: flex; flex-direction: column; gap: 8px; }
.src-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 8px 10px;
}
.src-icon-box {
  width: 26px; height: 26px; border-radius: 6px;
  background: #e6f0ff; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; margin-top: 1px;
}
.src-text { flex: 1; min-width: 0; }
.src-name { font-size: 12px; color: #1d2129; font-weight: 500; word-break: break-all; }
.src-meta { font-size: 11px; color: #86909c; margin-top: 2px; }
.src-del  { color: #c2c7d0; cursor: pointer; flex-shrink: 0; font-size: 14px; margin-top: 2px; }
.src-del:hover { color: #ff4d4f; }

/* 面板底部 */
.ep-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 0 0 10px 10px;
  padding: 12px 16px;
  border-top: 1px solid #f0f2f5;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.ep-footer-r { display: flex; gap: 8px; }

/* ── 待补 FAQ 推荐 ── */
.suggest-section {
  margin-top: 16px;
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.suggest-hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.suggest-title { margin: 0 0 4px; font-size: 15px; font-weight: 600; color: #1d2129; }
.suggest-sub { margin: 0; font-size: 12px; color: #86909c; line-height: 1.5; }
.suggest-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.suggest-days { width: 108px; }
.suggest-list { display: flex; flex-direction: column; gap: 10px; min-height: 60px; }
.suggest-empty { padding: 24px; text-align: center; color: #86909c; font-size: 13px; }
.suggest-card {
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 12px 14px;
  background: #fafbfc;
}
.suggest-card-top {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}
.suggest-rank {
  width: 22px; height: 22px; border-radius: 6px;
  background: #e6f0ff; color: #1677ff; font-size: 12px; font-weight: 600;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.suggest-q { flex: 1; font-size: 14px; color: #1d2129; font-weight: 500; line-height: 1.5; }
.suggest-meta {
  display: flex; flex-wrap: wrap; gap: 10px;
  font-size: 12px; color: #86909c; margin-bottom: 8px;
}
.suggest-answer {
  font-size: 12px; color: #4e5969; line-height: 1.6;
  background: #fff; border-radius: 8px; padding: 10px 12px;
  border: 1px dashed #e5e6eb; margin-bottom: 10px;
  max-height: 120px; overflow: hidden;
}
.suggest-card-ft { display: flex; justify-content: flex-end; }

/* ── 高频推荐 ── */
.hot-box {
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.hot-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.hot-title { font-size: 13px; font-weight: 600; color: #1d2129; }
.hot-empty { font-size: 12px; color: #c2c7d0; text-align: center; padding: 12px 0; }
.hot-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 0;
  border-bottom: 1px dashed #f5f7fa;
  cursor: default;
}
.hot-row:last-of-type { border-bottom: none; }
.hot-rank {
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
  min-width: 22px;
  text-align: left;
  flex-shrink: 0;
}
.hot-rank.fire { color: #1d2129; }
.hot-q {
  flex: 1;
  font-size: 12px;
  color: #4e5969;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}
.hot-q:hover { color: #1677ff; }
.hot-cnt {
  font-size: 11px;
  color: #86909c;
  white-space: nowrap;
  flex-shrink: 0;
}
.hot-more {
  text-align: center;
  font-size: 12px;
  color: #1677ff;
  cursor: pointer;
  margin-top: 10px;
}
.hot-more:hover { text-decoration: underline; }

/* ── 过渡动画 ── */
.panel-slide-enter-active,
.panel-slide-leave-active { transition: all .2s ease; }
.panel-slide-enter-from,
.panel-slide-leave-to { opacity: 0; transform: translateX(16px); }
</style>
