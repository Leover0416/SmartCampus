<template>
  <div class="knowledge-page">
    <div class="page-layout">

      <!-- ===================== LEFT: MAIN CONTENT ===================== -->
      <div class="main-col">

        <!-- Header -->
        <div class="page-header">
          <div>
            <h2 class="page-title">知识库管理</h2>
            <p class="page-subtitle">集中管理学校知识文档，支持上传、分类、分块与检索配置，为 RAG 检索提供高质量数据。</p>
          </div>
          <div class="header-btns">
            <el-button type="primary" @click="uploadDialogVisible = true">
              <el-icon><Upload /></el-icon> 上传文档
            </el-button>
            <el-button @click="categoryDialogVisible = true">
              <el-icon><FolderAdd /></el-icon> 新建分类
            </el-button>
          </div>
        </div>

        <!-- Stats Cards -->
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-icon blue"><el-icon :size="26"><Document /></el-icon></div>
            <div class="stat-body">
              <div class="stat-label">文档总数</div>
              <div class="stat-value">{{ stats.total }}</div>
              <div class="stat-sub positive">较上周 +{{ stats.weekNew }}</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon green"><el-icon :size="26"><CircleCheck /></el-icon></div>
            <div class="stat-body">
              <div class="stat-label">已就绪</div>
              <div class="stat-value">{{ stats.ready }}</div>
              <div class="stat-sub">{{ stats.readyPct }}%</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon orange"><el-icon :size="26"><Clock /></el-icon></div>
            <div class="stat-body">
              <div class="stat-label">处理中</div>
              <div class="stat-value">{{ stats.processing }}</div>
              <div class="stat-sub">{{ stats.processingPct }}%</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon purple"><el-icon :size="26"><DataAnalysis /></el-icon></div>
            <div class="stat-body">
              <div class="stat-label">
                检索命中率
                <el-tooltip content="近7天有结果的查询占总查询比例" placement="top">
                  <el-icon :size="12" style="cursor:help;opacity:.6"><InfoFilled /></el-icon>
                </el-tooltip>
              </div>
              <div class="stat-value">{{ stats.hitRate }}%</div>
              <div class="stat-sub" :class="stats.hitSubClass">{{ stats.hitSub }}</div>
            </div>
          </div>
        </div>

        <!-- Category Tabs -->
        <div class="cat-bar">
          <button
            v-for="cat in categories"
            :key="cat.code"
            class="cat-btn"
            :class="{ active: filters.categoryCode === cat.code }"
            @click="selectCategory(cat.code)"
          >{{ cat.name }}</button>
          <button class="cat-btn more-btn">
            更多 <el-icon><ArrowDown /></el-icon>
          </button>
        </div>

        <!-- Filter Row -->
        <div class="filter-row">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:116px" @change="currentPage=1">
            <el-option label="全部状态" value="" />
            <el-option label="已就绪" value="READY" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="处理失败" value="FAILED" />
          </el-select>
          <el-select v-model="filters.source" placeholder="全部来源" clearable style="width:116px" @change="currentPage=1">
            <el-option label="全部来源" value="" />
            <el-option label="教务处" value="教务处" />
            <el-option label="计算机学院" value="计算机学院" />
            <el-option label="学生处" value="学生处" />
          </el-select>
          <el-date-picker
            v-model="filters.dateRange" type="daterange"
            start-placeholder="更新时间" end-placeholder="结束时间"
            style="width:220px" @change="currentPage=1"
          />
          <el-input v-model="filters.keyword" placeholder="搜索文档名称" clearable style="width:200px;margin-left:auto"
            :prefix-icon="Search" @keyup.enter="currentPage=1" @clear="currentPage=1" />
          <el-button text style="color:#64748b" @click="resetFilters">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </div>

        <!-- Table -->
        <el-table
          :data="pagedDocs"
          v-loading="loading"
          row-key="docId"
          class="doc-table"
          :header-cell-style="{ background:'#f8fafc', color:'#64748b', fontSize:'13px', fontWeight:'500', padding:'10px 0' }"
        >
          <el-table-column label="文档名称" min-width="280">
            <template #default="{ row }">
              <div class="doc-cell">
                <div class="file-icon" :class="extClass(row.fileName)">
                  {{ extLabel(row.fileName) }}
                </div>
                <div class="doc-info">
                  <div class="doc-name">{{ row.title || row.fileName }}</div>
                  <div class="doc-meta">来源：{{ row.source || '教务处' }} · {{ extLabel(row.fileName) }} · {{ fmtSize(row.fileSize) }}</div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="分类" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" class="cat-label-tag">{{ catName(row.categoryCode) }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="可见范围" width="116">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" class="access-label-tag">{{ row.accessLevelName || accessLevelName(row.accessLevel) }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="status-chip" :class="'chip-' + (row.status || 'READY').toLowerCase()">
                <el-icon v-if="row.status === 'PROCESSING'" class="rotating"><Loading /></el-icon>
                {{ statusLabel(row.status) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="分块数" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.status === 'READY'" class="chunk-num">{{ (row.childChunkCount || 0).toLocaleString() }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>

          <el-table-column label="更新时间" width="148" sortable prop="updatedAt">
            <template #default="{ row }">
              <span class="time-text">{{ fmtDate(row.updatedAt || row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" :disabled="row.status === 'FAILED'" @click="handlePreview(row)">预览</el-button>
              <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
              <el-dropdown trigger="click" @command="cmd => handleCmd(cmd, row)">
                <el-button link size="small" style="color:#64748b">···</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="reindex">重新索引</el-dropdown-item>
                    <el-dropdown-item command="delete" divided style="color:#ef4444">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <!-- Pagination -->
        <div class="table-footer">
          <span class="total-tip">共 {{ filteredDocs.length }} 条</span>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="filteredDocs.length"
            layout="sizes, prev, pager, next"
            background small
          />
        </div>
      </div>

      <!-- ===================== RIGHT: SIDE PANEL ===================== -->
      <div class="right-col">

        <!-- Search Test -->
        <el-card class="search-panel" shadow="never">
          <template #header>
            <div class="panel-title">
              <el-icon color="#f59e0b" :size="16"><MagicStick /></el-icon>
              <span>检索测试</span>
            </div>
          </template>

          <div class="search-input-wrap">
            <el-input
              v-model="searchQuery"
              placeholder="学生手册里对缓考怎么规定？"
              @keyup.enter="runSearch"
            />
            <el-button type="primary" :loading="searching" circle class="search-send-btn" @click="runSearch">
              <el-icon><Promotion /></el-icon>
            </el-button>
          </div>

          <div v-if="searchDone" class="search-time">
            检索到 {{ searchResults.length }} 条相关结果（用时 {{ searchTime }}s）
          </div>

          <div v-if="searchResults.length" class="hit-list">
            <div v-for="(hit, idx) in searchResults.slice(0, 3)" :key="idx" class="hit-item">
              <div class="hit-top">
                <span class="hit-rank">{{ idx + 1 }}</span>
                <span class="hit-doc-name">{{ hit.docTitle }}</span>
                <span class="hit-chunk">· 分块 {{ hit.childId || (idx * 128 + 412) }}</span>
                <span class="hit-score-val">{{ hit.score?.toFixed(2) || (0.86 - idx * 0.14).toFixed(2) }}</span>
              </div>
              <div class="hit-text">{{ hit.content }}</div>
              <div class="hit-bottom">
                <span>来源：{{ hit.docTitle }}</span>
                <span>第 {{ hit.pageStart || (idx * 42 + 1) }} 页</span>
              </div>
            </div>
            <div class="view-all-link">查看全部结果 →</div>
          </div>
        </el-card>

        <!-- Bottom mini cards -->
        <div class="mini-row">
          <!-- Upload drop zone -->
          <div class="mini-upload" @click="uploadDialogVisible = true">
            <el-icon :size="40" color="#3b82f6"><UploadFilled /></el-icon>
            <p class="mini-upload-main">拖拽文件到此处，或 <span class="mini-link">点击上传</span></p>
            <p class="mini-upload-hint">支持 PDF、Word、Excel、PPT、Markdown 等格式<br>单文件不超过 100MB</p>
          </div>

          <!-- Overview pie -->
          <div class="mini-overview">
            <div class="mini-chart" ref="overviewChartEl"></div>
            <div class="mini-legend">
              <div v-for="item in overviewItems" :key="item.name" class="legend-row">
                <span class="legend-dot" :style="{ background: item.color }"></span>
                <span class="legend-name">{{ item.name }}</span>
                <span class="legend-val">{{ item.value }} ({{ item.pct }}%)</span>
              </div>
            </div>
            <div class="mini-footer">
              最后更新：2025-05-19 14:32
              <el-icon style="cursor:pointer;color:#94a3b8" @click="loadDocs"><Refresh /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===================== UPLOAD DIALOG ===================== -->
    <el-dialog v-model="uploadDialogVisible" title="上传文档" width="520px" @close="resetUpload">
      <el-form :model="uploadForm" :rules="uploadRules" ref="uploadFormRef" label-width="80px">
        <el-form-item label="文档标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入文档标题" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryCode">
          <el-select v-model="uploadForm.categoryCode" placeholder="选择分类" style="width:100%">
            <el-option v-for="cat in categoryOptions" :key="cat.code" :label="cat.name" :value="cat.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见范围" prop="accessLevel">
          <el-select v-model="uploadForm.accessLevel" placeholder="选择可见范围" style="width:100%">
            <el-option label="全部可见" :value="0" />
            <el-option label="仅教师可见" :value="1" />
            <el-option label="仅学生可见" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件" prop="file">
          <el-upload
            ref="uploadRef" :auto-upload="false" :limit="1"
            :on-change="onFileChange"
            :on-exceed="() => ElMessage.warning('一次只能上传一个文件')"
            accept=".pdf,.docx,.doc,.txt,.md,.markdown,.csv,.xlsx,.xls,.pptx,.ppt" drag class="upload-zone"
          >
            <el-icon :size="36" color="#3b82f6"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
            <template #tip>
              <div class="upload-tip">支持 PDF / Word / Excel / PPT / Markdown / TXT / CSV，文件大小 ≤ 100MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="uploadProgress > 0" label="上传进度">
          <el-progress :percentage="uploadProgress" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload" :loading="uploading">开始上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="categoryDialogVisible" title="新建分类" width="420px" @close="resetCategoryForm">
      <el-form :model="categoryForm" :rules="categoryRules" ref="categoryFormRef" label-width="82px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="categoryForm.name" placeholder="例如：奖助学金" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="分类编码" prop="code">
          <el-input v-model="categoryForm.code" placeholder="例如：scholarship" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="previewTitle" width="82vw" class="preview-dialog" @closed="resetPreview">
      <div v-loading="previewLoading" class="preview-body">
        <iframe
          v-if="previewKind === 'pdf'"
          :src="previewUrl"
          class="preview-frame"
        ></iframe>
        <pre v-else-if="previewKind === 'text'" class="preview-text">{{ previewText }}</pre>
        <div v-else class="preview-fallback">
          <el-icon :size="42" color="#94a3b8"><Document /></el-icon>
          <p>该文件类型不支持内嵌预览</p>
          <el-button type="primary" @click="openPreviewInNewTab">打开原文件</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="openPreviewInNewTab">新窗口打开</el-button>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Upload, FolderAdd, Document, CircleCheck, Clock,
  DataAnalysis, InfoFilled, ArrowDown, Search, Refresh, Loading,
  MagicStick, Promotion, UploadFilled
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { knowledgeApi } from '@/api/knowledge'
import { dashboardApi } from '@/api/dashboard'
import * as echarts from 'echarts'

const authStore = useAuthStore()

// ───── Categories ─────
const defaultCategories = [
  { code: '',                name: '全部分类' },
  { code: 'school_overview', name: '学校概况' },
  { code: 'college_policy',  name: '学院政策' },
  { code: 'student_handbook',name: '学生手册' },
  { code: 'training_plan',   name: '培养方案' },
  { code: 'announcement',    name: '通知公告' },
  { code: 'academic_info',   name: '教务信息' },
]
const categories = ref(defaultCategories)
const categoryOptions = computed(() => categories.value.filter(c => c.code))
const catMap = computed(() => Object.fromEntries(categories.value.map(c => [c.code, c.name])))
function catName(code) { return catMap.value[code] || code || '—' }

async function loadCategories() {
  try {
    const res = await knowledgeApi.categories()
    const loaded = res.data || []
    categories.value = [{ code: '', name: '全部分类' }, ...loaded]
  } catch {
    categories.value = defaultCategories
  }
}

// ───── State ─────
const docs    = ref([])
const loading = ref(false)
const filters = reactive({ categoryCode: '', status: '', source: '', keyword: '', dateRange: null })
const currentPage = ref(1)
const pageSize    = ref(10)

function selectCategory(code) {
  filters.categoryCode = code
  currentPage.value = 1
}
function resetFilters() {
  Object.assign(filters, { categoryCode: '', status: '', source: '', keyword: '', dateRange: null })
  currentPage.value = 1
}

async function loadDocs() {
  loading.value = true
  try {
    const res = await knowledgeApi.list({
      categoryCode: filters.categoryCode || undefined,
      status: filters.status || undefined
    })
    docs.value = Array.isArray(res.data) ? res.data : []
  } catch {
    docs.value = []
  } finally {
    loading.value = false
  }
}

// Filtered + paged
const filteredDocs = computed(() => {
  return docs.value.filter(d => {
    if (filters.categoryCode && d.categoryCode !== filters.categoryCode) return false
    if (filters.status && d.status !== filters.status) return false
    if (filters.source && d.source !== filters.source) return false
    if (filters.keyword && !(d.title || d.fileName || '').includes(filters.keyword)) return false
    return true
  })
})
const pagedDocs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredDocs.value.slice(start, start + pageSize.value)
})

// ───── Dashboard metrics（检索命中率，与数据看板同源）─────
const dashboardMetrics = ref({
  knowledgeHitRate: 0,
  knowledgeHitCount: 0,
  totalRequests: 0,
  citationRate: 0,
})

async function loadDashboardMetrics() {
  try {
    const res = await dashboardApi.get(7)
    const m = res.data?.metrics || {}
    dashboardMetrics.value = {
      knowledgeHitRate: Number(m.knowledgeHitRate) || 0,
      knowledgeHitCount: Number(m.knowledgeHitCount) || 0,
      totalRequests: Number(m.totalRequests) || 0,
      citationRate: Number(m.citationRate) || 0,
    }
  } catch {
    dashboardMetrics.value = { knowledgeHitRate: 0, knowledgeHitCount: 0, totalRequests: 0, citationRate: 0 }
  }
}

// ───── Stats ─────
const stats = computed(() => {
  const total = docs.value.length
  const ready = docs.value.filter(d => d.status === 'READY').length
  const processing = docs.value.filter(d => d.status === 'PROCESSING').length
  const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
  const weekNew = docs.value.filter(d => {
    const time = new Date(d.createdAt || d.updatedAt || 0).getTime()
    return Number.isFinite(time) && time >= sevenDaysAgo
  }).length
  const dm = dashboardMetrics.value
  const hitRate = dm.totalRequests > 0
    ? Number(dm.knowledgeHitRate).toFixed(1)
    : '0.0'
  const hitSub = dm.totalRequests > 0
    ? `近7天命中 ${dm.knowledgeHitCount}/${dm.totalRequests} 次`
    : '近7天暂无问答数据'
  return {
    total,
    weekNew,
    ready,
    readyPct:      total ? ((ready / total) * 100).toFixed(1) : '0.0',
    processing,
    processingPct: total ? ((processing / total) * 100).toFixed(1) : '0.0',
    hitRate,
    hitSub,
    hitSubClass: dm.knowledgeHitCount > 0 ? 'positive' : '',
  }
})

// ───── Overview chart data ─────
const overviewColors = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#64748b']
const overviewItems = computed(() => {
  const total = docs.value.length
  if (!total) return []
  const counts = docs.value.reduce((acc, doc) => {
    const code = doc.categoryCode || ''
    acc[code] = (acc[code] || 0) + 1
    return acc
  }, {})
  return Object.entries(counts).map(([code, value], index) => ({
    name: catName(code),
    value,
    pct: ((value / total) * 100).toFixed(1),
    color: overviewColors[index % overviewColors.length]
  }))
})

const overviewChartEl = ref(null)
let overviewChart = null

function initOverviewChart() {
  if (!overviewChartEl.value) return
  overviewChart = echarts.init(overviewChartEl.value)
  updateOverviewChart()
}

function updateOverviewChart() {
  if (!overviewChart) return
  overviewChart.setOption({
    series: [{
      type: 'pie', radius: ['55%', '80%'],
      data: overviewItems.value.map(i => ({ name: i.name, value: i.value, itemStyle: { color: i.color } })),
      label: { show: false },
      emphasis: { scale: true, scaleSize: 4 }
    }]
  })
}

// ───── Search Test ─────
const searchQuery   = ref('')
const searching     = ref(false)
const searchDone    = ref(false)
const searchTime    = ref('0.78')
const searchResults = ref([])

async function runSearch() {
  if (!searchQuery.value.trim()) { ElMessage.warning('请输入检索内容'); return }
  searching.value = true
  searchDone.value = false
  searchResults.value = []
  const t0 = Date.now()
  try {
    const res = await knowledgeApi.searchTest(searchQuery.value, 10)
    searchResults.value = res.data?.hits || []
    searchTime.value = ((Date.now() - t0) / 1000).toFixed(2)
  } catch {
    searchResults.value = []
    searchTime.value = ((Date.now() - t0) / 1000).toFixed(2)
  } finally {
    searching.value = false
    searchDone.value = true
  }
}

// ───── Upload ─────
const uploadDialogVisible = ref(false)
const uploadFormRef       = ref()
const uploadRef           = ref()
const uploading           = ref(false)
const uploadProgress      = ref(0)
const uploadForm = reactive({ title: '', categoryCode: 'student_handbook', accessLevel: 0, file: null })
const uploadRules = {
  title: [{ required: true, message: '请输入文档标题', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请选择分类', trigger: 'change' }],
  accessLevel: [{ required: true, message: '请选择可见范围', trigger: 'change' }]
}

// ───── Category create ─────
const categoryDialogVisible = ref(false)
const categoryFormRef = ref()
const categorySaving = ref(false)
const categoryForm = reactive({ name: '', code: '' })
const categoryRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{1,49}$/, message: '使用小写字母、数字、下划线，并以字母开头', trigger: 'blur' }
  ]
}

function resetCategoryForm() {
  categoryForm.name = ''
  categoryForm.code = ''
  categorySaving.value = false
  categoryFormRef.value?.clearValidate()
}

async function submitCategory() {
  await categoryFormRef.value?.validate()
  categorySaving.value = true
  try {
    const res = await knowledgeApi.createCategory({
      name: categoryForm.name.trim(),
      code: categoryForm.code.trim()
    })
    ElMessage.success('分类已创建')
    categoryDialogVisible.value = false
    await loadCategories()
    if (res.data?.code) uploadForm.categoryCode = res.data.code
  } catch {
    // handled by request interceptor
  } finally {
    categorySaving.value = false
  }
}

// ───── Preview ─────
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTitle   = ref('')
const previewUrl     = ref('')
const previewKind    = ref('')
const previewText    = ref('')
const previewObjectUrl = ref('')

function onFileChange(file) { uploadForm.file = file.raw }
function resetUpload() {
  uploadForm.title = ''; uploadForm.categoryCode = 'student_handbook'; uploadForm.accessLevel = 0; uploadForm.file = null
  uploadProgress.value = 0; uploading.value = false
  uploadRef.value?.clearFiles()
}

async function submitUpload() {
  await uploadFormRef.value?.validate()
  if (!uploadForm.file) { ElMessage.warning('请选择要上传的文件'); return }
  uploading.value = true
  const fd = new FormData()
  fd.append('file', uploadForm.file)
  fd.append('title', uploadForm.title)
  fd.append('categoryCode', uploadForm.categoryCode)
  fd.append('accessLevel', uploadForm.accessLevel)
  try {
    await knowledgeApi.upload(fd, e => { uploadProgress.value = Math.round(e.loaded / e.total * 100) })
    ElMessage.success('文件已上传，正在后台处理中...')
    uploadDialogVisible.value = false
    resetUpload(); loadDocs()
  } catch { /* handled */ } finally { uploading.value = false }
}

// ───── Row actions ─────
async function handlePreview(row) {
  if (row.status === 'FAILED') {
    ElMessage.warning(row.errorMsg || '文档处理失败，无法预览')
    return
  }
  previewTitle.value = row.title || row.fileName || '文档预览'
  previewKind.value = previewType(row.fileName || row.title)
  previewText.value = ''
  previewVisible.value = true
  previewLoading.value = true

  try {
    if (previewKind.value === 'text') {
      previewText.value = await knowledgeApi.previewText(row.docId)
    } else {
      const blob = await knowledgeApi.previewBlob(row.docId)
      previewObjectUrl.value = URL.createObjectURL(blob)
      previewUrl.value = previewObjectUrl.value
    }
  } catch {
    previewKind.value = 'other'
    ElMessage.error('预览加载失败')
  } finally {
    previewLoading.value = false
  }
}
async function handleDownload(row) {
  try {
    const blob = await knowledgeApi.downloadBlob(row.docId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.fileName || row.title || 'document'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}
async function handleCmd(cmd, row) {
  if (cmd === 'reindex') {
    await ElMessageBox.confirm(`确定重新索引《${row.title}》？将清除旧分块并重新向量化。`, '重新索引确认', { type: 'warning' })
    try {
      const res = await knowledgeApi.reindex(row.docId)
      const updated = res.data || { ...row, status: 'PROCESSING', parentChunkCount: 0, childChunkCount: 0 }
      docs.value = docs.value.map(d => d.docId === row.docId ? { ...d, ...updated } : d)
      ElMessage.success('已提交重新索引')
      loadDocs()
    } catch { /* handled */ }
    return
  }
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除《${row.title}》？将同步清除向量索引。`, '删除确认', { type: 'warning' })
    try {
      await knowledgeApi.delete(row.docId)
      ElMessage.success('已删除')
      docs.value = docs.value.filter(d => d.docId !== row.docId)
    } catch { /* handled */ }
  }
}

// ───── Helpers ─────
function extLabel(fileName) {
  const ext = (fileName || '').split('.').pop().toUpperCase()
  return ext || 'FILE'
}
function extClass(fileName) {
  const ext = (fileName || '').split('.').pop().toLowerCase()
  if (ext === 'pdf') return 'icon-pdf'
  if (ext === 'docx' || ext === 'doc') return 'icon-docx'
  if (ext === 'xlsx' || ext === 'xls' || ext === 'csv') return 'icon-sheet'
  if (ext === 'pptx' || ext === 'ppt') return 'icon-slide'
  if (ext === 'md' || ext === 'markdown' || ext === 'txt') return 'icon-text'
  return 'icon-other'
}
function fmtSize(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024)        return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}
function fmtDate(dt) {
  if (!dt) return '—'
  const d = new Date(dt)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function statusLabel(s) {
  return { READY: '已就绪', PROCESSING: '处理中', FAILED: '处理失败' }[s] || s || '—'
}
function accessLevelName(level) {
  return { 0: '全部可见', 1: '仅教师可见', 2: '仅教师可见', 3: '仅学生可见' }[level] || '未知'
}
function previewType(fileName) {
  const lower = (fileName || '').toLowerCase()
  if (lower.endsWith('.pdf')) return 'pdf'
  if (
    lower.endsWith('.txt') ||
    lower.endsWith('.md') ||
    lower.endsWith('.markdown') ||
    lower.endsWith('.csv') ||
    lower.endsWith('.doc') ||
    lower.endsWith('.docx') ||
    lower.endsWith('.xls') ||
    lower.endsWith('.xlsx') ||
    lower.endsWith('.ppt') ||
    lower.endsWith('.pptx')
  ) return 'text'
  return 'other'
}
function resetPreview() {
  if (previewObjectUrl.value) {
    URL.revokeObjectURL(previewObjectUrl.value)
  }
  previewLoading.value = false
  previewTitle.value = ''
  previewUrl.value = ''
  previewObjectUrl.value = ''
  previewKind.value = ''
  previewText.value = ''
}
function openPreviewInNewTab() {
  if (previewUrl.value) window.open(previewUrl.value, '_blank', 'noopener')
}

// ───── Lifecycle ─────
onMounted(async () => {
  await loadCategories()
  await Promise.all([loadDocs(), loadDashboardMetrics()])
  await nextTick()
  initOverviewChart()
})
watch(overviewItems, updateOverviewChart)
onUnmounted(() => { overviewChart?.dispose() })
</script>

<style scoped>
.knowledge-page { height: 100%; overflow: auto; background: #f5f7fa; }

/* ── Two-column layout ── */
.page-layout {
  display: flex;
  gap: 16px;
  padding: 24px;
  min-height: 100%;
  box-sizing: border-box;
}

/* ── Main column ── */
.main-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ── Right panel ── */
.right-col {
  width: 310px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── Header ── */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.page-title   { margin: 0; font-size: 20px; font-weight: 700; color: #1e293b; }
.page-subtitle{ margin: 4px 0 0; font-size: 13px; color: #64748b; line-height: 1.5; }
.header-btns  { display: flex; gap: 8px; flex-shrink: 0; }

/* ── Stats row ── */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.stat-icon {
  width: 52px; height: 52px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.stat-icon.blue   { background: #f0f5ff; color: #1677ff; }
.stat-icon.green  { background: #f0fdf4; color: #22c55e; }
.stat-icon.orange { background: #fff7ed; color: #f97316; }
.stat-icon.purple { background: #faf5ff; color: #a855f7; }
.stat-label  { font-size: 12px; color: #64748b; display: flex; align-items: center; gap: 4px; }
.stat-value  { font-size: 26px; font-weight: 700; color: #1e293b; line-height: 1.2; margin: 2px 0; }
.stat-sub    { font-size: 12px; color: #94a3b8; }
.stat-sub.positive { color: #22c55e; }

/* ── Category tabs ── */
.cat-bar {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  background: #fff;
  padding: 10px 12px;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.cat-btn {
  padding: 5px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: transparent;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all .15s;
  display: flex; align-items: center; gap: 3px;
}
.cat-btn:hover  { border-color: #1677ff; color: #1677ff; }
.cat-btn.active { background: #1677ff; border-color: #1677ff; color: #fff; font-weight: 500; }
.more-btn       { color: #94a3b8; border-style: dashed; }

/* ── Filter row ── */
.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  padding: 10px 14px;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}

/* ── Table ── */
.doc-table { background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.doc-table :deep(.el-table__row) { transition: background .1s; }
.doc-table :deep(.el-table__row:hover > td) { background: #f8faff !important; }

.doc-cell {
  display: flex; align-items: center; gap: 10px;
}
.file-icon {
  width: 36px; height: 40px;
  border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700; color: #fff;
  flex-shrink: 0; letter-spacing: 0;
}
.icon-pdf  { background: #ef4444; }
.icon-docx { background: #3b82f6; }
.icon-sheet{ background: #16a34a; }
.icon-slide{ background: #f97316; }
.icon-text { background: #64748b; }
.icon-other{ background: #94a3b8; }

.doc-name { font-size: 13px; font-weight: 500; color: #1e293b; }
.doc-meta { font-size: 12px; color: #94a3b8; margin-top: 2px; }

.cat-label-tag { border-color: #c7d9ff; color: #1677ff; background: #f0f5ff; }
.access-label-tag { border-color: #d8e0ec; color: #475569; background: #f8fafc; }

.status-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: 10px;
  font-size: 12px; font-weight: 500;
}
.chip-ready      { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }
.chip-processing { background: #fff7ed; color: #ea580c; border: 1px solid #fed7aa; }
.chip-failed     { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }

.rotating { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.chunk-num  { font-size: 13px; color: #475569; }
.text-muted { color: #cbd5e1; }
.time-text  { font-size: 12px; color: #64748b; }

/* ── Pagination ── */
.table-footer {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px;
  background: #fff;
  border-radius: 0 0 10px 10px;
  margin-top: -1px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.total-tip { font-size: 13px; color: #64748b; }

/* ── Right Panel: Search ── */
.search-panel { border-radius: 12px; border: 1px solid #e2e8f0; }
.search-panel :deep(.el-card__header) { padding: 12px 16px; border-bottom: 1px solid #f1f5f9; }
.search-panel :deep(.el-card__body)   { padding: 12px 16px; }
.panel-title { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; color: #1e293b; }

.search-input-wrap { display: flex; gap: 8px; align-items: center; }
.search-input-wrap .el-input { flex: 1; }
.search-send-btn { flex-shrink: 0; }

.search-time { font-size: 12px; color: #94a3b8; margin: 8px 0 4px; }

.hit-list { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.hit-item {
  background: #f8fafc; border-radius: 8px; padding: 10px 12px;
  border: 1px solid #e2e8f0;
}
.hit-top   { display: flex; align-items: center; gap: 5px; margin-bottom: 5px; flex-wrap: wrap; }
.hit-rank  { width: 18px; height: 18px; background: #1677ff; color: #fff; border-radius: 50%; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.hit-doc-name { font-size: 12px; font-weight: 500; color: #1e293b; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hit-chunk { font-size: 11px; color: #94a3b8; flex-shrink: 0; }
.hit-score-val { font-size: 12px; font-weight: 600; color: #22c55e; margin-left: auto; flex-shrink: 0; }
.hit-text  { font-size: 12px; color: #475569; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.hit-bottom{ display: flex; justify-content: space-between; font-size: 11px; color: #94a3b8; margin-top: 5px; }
.view-all-link { font-size: 12px; color: #1677ff; cursor: pointer; text-align: center; padding: 4px 0; }
.view-all-link:hover { text-decoration: underline; }

/* ── Mini cards row ── */
.mini-row { display: flex; gap: 10px; }

.mini-upload {
  flex: 1; min-width: 0;
  background: #fff;
  border: 1.5px dashed #bfdbfe;
  border-radius: 10px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 14px 8px; cursor: pointer; text-align: center;
  transition: border-color .2s;
}
.mini-upload:hover { border-color: #3b82f6; }
.mini-upload-main { margin: 6px 0 2px; font-size: 11px; color: #64748b; line-height: 1.4; }
.mini-upload-hint { margin: 0; font-size: 10px; color: #94a3b8; line-height: 1.5; }
.mini-link { color: #3b82f6; }

.mini-overview {
  flex: 1; min-width: 0;
  background: #fff;
  border-radius: 10px;
  padding: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.mini-chart {
  width: 100%; height: 80px;
}
.mini-legend { margin-top: 4px; }
.legend-row  { display: flex; align-items: center; gap: 4px; font-size: 10px; color: #475569; margin-bottom: 2px; }
.legend-dot  { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.legend-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.legend-val  { color: #94a3b8; flex-shrink: 0; }
.mini-footer { font-size: 10px; color: #94a3b8; margin-top: 6px; display: flex; justify-content: space-between; align-items: center; }

/* ── Upload dialog ── */
.upload-zone { width: 100%; }
.upload-tip  { font-size: 12px; color: #94a3b8; }

.preview-dialog :deep(.el-dialog__body) { padding: 0; }
.preview-body {
  height: min(72vh, 760px);
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
}
.preview-frame {
  width: 100%;
  height: 100%;
  border: 0;
  background: #fff;
}
.preview-text {
  height: 100%;
  margin: 0;
  padding: 18px 22px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
  color: #1e293b;
  font: 13px/1.7 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.preview-fallback {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #64748b;
}
</style>
