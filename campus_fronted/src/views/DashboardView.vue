<template>
  <div class="dashboard-page">
    <!-- ── 页面头部 ── -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">数据看板</h2>
        <p class="page-subtitle">全面了解校园智能助理的运行情况与服务效果</p>
      </div>
      <div class="header-right">
        <span class="date-range-label">{{ dateDisplayStr }}</span>
        <el-icon :size="15" color="#c2c7d0" style="cursor:pointer"><Calendar /></el-icon>
        <el-radio-group v-model="days" @change="loadData" size="small" class="days-radio">
          <el-radio-button :value="7">近7天</el-radio-button>
          <el-radio-button :value="14">近14天</el-radio-button>
          <el-radio-button :value="30">近30天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" size="small" circle @click="loadData" :loading="loading" />
      </div>
    </div>

    <div v-loading="loading" class="dashboard-body">
      <!-- ── 6 个指标卡 ── -->
      <div class="metrics-grid">
        <div class="metric-card" v-for="m in metricCards" :key="m.key">
          <div class="mc-icon" :style="{ background: m.bg }">
            <el-icon :size="22" :color="m.color"><component :is="m.icon" /></el-icon>
          </div>
          <div class="mc-body">
            <div class="mc-label">{{ m.label }}</div>
            <div class="mc-value">{{ formatMetric(metrics[m.key], m) }}</div>
            <div class="mc-trend" :class="getTrendClass(m)">
              <el-icon :size="11" v-if="getTrendDirection(m) !== undefined">
                <component :is="getTrendDirection(m) ? 'Top' : 'Bottom'" />
              </el-icon>
              <span>{{ getTrendText(m) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ── 图表第一行：会话趋势 | 意图分布 | 热门问题分类 ── -->
      <div class="charts-row row1">
        <!-- 会话趋势 -->
        <el-card shadow="never" class="chart-card trend-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">会话趋势</span>
              <el-icon :size="14" color="#c2c7d0"><InfoFilled /></el-icon>
              <div style="flex:1" />
              <el-select size="small" v-model="trendGranularity" style="width:80px" @change="loadData">
                <el-option label="按天" value="day" />
                <el-option label="按周" value="week" />
              </el-select>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-box h220" />
        </el-card>

        <!-- 意图分布 -->
        <el-card shadow="never" class="chart-card intent-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">意图分布</span>
              <el-icon :size="14" color="#c2c7d0"><InfoFilled /></el-icon>
              <div style="flex:1" />

            </div>
          </template>
          <div ref="intentChartRef" class="chart-box h220" />
        </el-card>

        <!-- 热门问题分类 -->
        <el-card shadow="never" class="chart-card category-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">热门问题分类</span>
              <el-icon :size="14" color="#c2c7d0"><InfoFilled /></el-icon>
              <div style="flex:1" />
<!--              <span class="card-more">更多 ›</span>-->
            </div>
          </template>
          <div class="category-list">
            <div class="cat-header-row">
              <span>分类</span>
              <span style="margin-left:auto">会话占比</span>
            </div>
            <div v-for="cat in hotCategories" :key="cat.name" class="cat-row">
              <span class="cat-name">{{ cat.name }}</span>
              <div class="cat-bar-wrap">
                <div class="cat-bar-fill" :style="{ width: cat.pct + '%' }" />
              </div>
              <span class="cat-pct">{{ cat.pct }}%</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- ── 图表第二行：工具调用 | 知识库命中率 | 人工接管趋势 | TOP10 ── -->
      <div class="charts-row row2">
        <!-- 工具调用次数 -->
        <el-card shadow="never" class="chart-card tool-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">工具调用次数</span>
              <el-icon :size="14" color="#c2c7d0"><InfoFilled /></el-icon>
              <div style="flex:1" />
<!--              <span class="card-more">更多 ›</span>-->
            </div>
          </template>
          <div ref="toolChartRef" class="chart-box h200" />
        </el-card>

        <!-- 知识库命中率 / 引用率 -->
        <el-card shadow="never" class="chart-card hit-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">知识库命中率 / 引用率</span>
              <el-icon :size="14" color="#c2c7d0"><InfoFilled /></el-icon>
              <div style="flex:1" />
<!--              <span class="card-more">更多 ›</span>-->
            </div>
          </template>
          <div class="hit-rate-row">
            <div class="hit-gauge-wrap">
              <div class="hit-gauge-title">知识库命中率</div>
              <div ref="hitChartRef" class="gauge-box" />
              <div class="hit-big-val">{{ formatPercent(metrics.knowledgeHitRate) }}</div>
              <div class="hit-trend neutral">基于当前筛选周期</div>
              <div class="hit-hint">命中次数：{{ formatNumber(metrics.knowledgeHitCount) }}</div>
            </div>
            <div class="hit-divider" />
            <div class="hit-gauge-wrap">
              <div class="hit-gauge-title">知识库引用率</div>
              <div ref="citeChartRef" class="gauge-box" />
              <div class="hit-big-val">{{ formatPercent(metrics.citationRate) }}</div>
              <div class="hit-trend neutral">基于助手引用消息</div>
              <div class="hit-hint">引用次数：{{ formatNumber(metrics.knowledgeReferenceCount) }}</div>
            </div>
          </div>
        </el-card>

        <!-- 人工接管会话趋势 -->
        <el-card shadow="never" class="chart-card takeover-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">人工接管会话趋势</span>
              <el-icon :size="14" color="#c2c7d0"><InfoFilled /></el-icon>
              <div style="flex:1" />
<!--              <span class="card-more">更多 ›</span>-->
            </div>
          </template>
          <div ref="takeoverChartRef" class="chart-box h200" />
        </el-card>

        <!-- 热门咨询问题 TOP10 -->
        <el-card shadow="never" class="chart-card top10-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">热门咨询问题 TOP10</span>
              <div style="flex:1" />
<!--              <span class="card-more">更多 ›</span>-->
            </div>
          </template>
          <div class="top10-head-row">
            <span>排名</span>
            <span>问题</span>
            <span>会话数</span>
          </div>
          <div class="top-query-list">
            <el-empty v-if="!topQueries.length" description="暂无数据" :image-size="40" />
            <div v-for="(q, i) in topQueries" :key="i" class="top-item">
              <span class="rank-badge" :class="`rank-${i < 3 ? i + 1 : ''}`">{{ i + 1 }}</span>
              <span class="query-text">{{ q.user_query || q.userQuery }}</span>
              <span class="query-cnt">{{ formatNumber(q.cnt) }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- ── 底部时间戳 ── -->
      <div class="page-footer">
        <el-icon :size="12"><InfoFilled /></el-icon>
        <span>所有数据均为北京时间，最后更新：{{ lastUpdateTime }}</span>
        <el-button text size="small" :icon="Refresh" @click="loadData" style="margin-left:2px">刷新</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, markRaw } from 'vue'
import {
  Refresh, ChatDotRound, Message, Ticket, FolderOpened, Timer,
  Top, Bottom, InfoFilled, DataAnalysis, Calendar, UserFilled
} from '@element-plus/icons-vue'
import { dashboardApi } from '../api/dashboard'
import * as echarts from 'echarts'

// ── 状态 ──────────────────────────────────────────────────────────────────
const days             = ref(7)
const trendGranularity = ref('day')
const loading          = ref(false)
const metrics          = reactive({})
const topQueries       = ref([])
const hotCategories    = ref([])
const lastUpdateTime   = ref('—')

// ── Chart refs ────────────────────────────────────────────────────────────
const trendChartRef    = ref(null)
const intentChartRef   = ref(null)
const toolChartRef     = ref(null)
const hitChartRef      = ref(null)
const citeChartRef     = ref(null)
const takeoverChartRef = ref(null)

let trendChart = null, intentChart = null, toolChart = null
let hitChart   = null, citeChart   = null, takeoverChart = null

// ── 指标卡配置 ────────────────────────────────────────────────────────────
const metricCards = [
  {
    key: 'totalRequests', label: '总会话数',
    icon: markRaw(ChatDotRound), color: '#1677ff', bg: '#e6f0ff',
    trendKey: 'totalRequestsTrend', trendLabel: `较前${days.value}天`, trendPct: true
  },
  {
    key: 'sessionsToday', label: '今日会话',
    icon: markRaw(Message), color: '#17a855', bg: '#f0fdf4',
    trendKey: 'sessionsTodayTrend', trendLabel: '较昨日', trendPct: true
  },
  {
    key: 'satisfaction', label: '满意度',
    icon: markRaw(DataAnalysis), color: '#fa8c16', bg: '#fff7e6',
    trendLabel: '来自工单评分', pct: true
  },
  {
    key: 'transferRate', label: '转人工率',
    icon: markRaw(UserFilled), color: '#7b5ea7', bg: '#f5f0ff',
    trendKey: 'transferRateTrend', trendLabel: `较前${days.value}天`, trendPct: true, pct: true, inverseTrend: true
  },
  {
    key: 'readyDocs', label: '知识库文档数',
    icon: markRaw(FolderOpened), color: '#0d9488', bg: '#e6fcf5',
    trendLabel: 'READY 状态文档'
  },
  {
    key: 'avgResponseMs', label: '平均响应时长',
    icon: markRaw(Timer), color: '#9333ea', bg: '#f3e8ff',
    trendKey: 'avgResponseMsTrend', trendLabel: `较前${days.value}天`, ms: true, inverseTrend: true
  },
]

// ── 意图颜色映射 ──────────────────────────────────────────────────────────
const intentColors = ['#1677ff', '#52c41a', '#fa8c16', '#7b5ea7', '#86909c']
const intentMap    = {
  POLICY_QA:     '政策问答',
  DOC_SEARCH:    '文档检索',
  ACADEMIC_TOOL: '教务工具',
  CHITCHAT:      '闲聊咨询',
  HUMAN:         '人工服务',
  HUMAN_HANDOFF: '人工服务',
}

const toolNameMap = {
  query_course_selection: '选课查询',
  query_academic_calendar: '校历查询',
  query_department_contact: '院系联系方式',
  create_human_ticket: '创建人工工单',
  human_handoff: '转人工服务',
}

// ── 日期显示 ──────────────────────────────────────────────────────────────
const dateDisplayStr = computed(() => {
  const end   = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days.value + 1)
  const fmt = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return `${fmt(start)} ~ ${fmt(end)}`
})

// ── 格式化函数 ────────────────────────────────────────────────────────────
function formatMetric(val, m) {
  if (val === undefined || val === null) return '—'
  if (m.pct) return val + '%'
  if (m.ms)  return val >= 1000 ? (val / 1000).toFixed(1) + 's' : val + 'ms'
  if (typeof val === 'number' && val >= 1000) return val.toLocaleString()
  return val
}

function formatPercent(val) {
  if (val === undefined || val === null) return '0%'
  return `${Number(val).toFixed(1).replace(/\.0$/, '')}%`
}

function formatNumber(val) {
  return Number(val || 0).toLocaleString()
}

function formatToolName(name) {
  if (!name) return '未知工具'
  if (toolNameMap[name]) return toolNameMap[name]
  return name
    .replace(/^query_/, '')
    .replace(/^create_/, '')
    .split('_')
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function getMetricTrendValue(m) {
  return m.trendKey ? Number(metrics[m.trendKey] || 0) : null
}

function getTrendDirection(m) {
  const value = getMetricTrendValue(m)
  if (value === null || value === 0) return undefined
  const up = value > 0
  return m.inverseTrend ? !up : up
}

function getTrendClass(m) {
  const direction = getTrendDirection(m)
  if (direction === undefined) return 'neutral'
  return direction ? 'up' : 'down'
}

function getTrendText(m) {
  const value = getMetricTrendValue(m)
  if (value === null) return m.trendLabel
  const prefix = value > 0 ? '+' : value < 0 ? '-' : ''
  const formatted = m.ms
    ? `${prefix}${formatMetric(Math.abs(value), { ms: true })}`
    : `${prefix}${value}${m.trendPct ? '%' : ''}`
  return `${m.trendLabel} ${formatted}`
}

// ── 生命周期 ──────────────────────────────────────────────────────────────
onMounted(async () => {
  await loadData()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  intentChart?.dispose()
  toolChart?.dispose()
  hitChart?.dispose()
  citeChart?.dispose()
  takeoverChart?.dispose()
})

// ── 数据加载 ──────────────────────────────────────────────────────────────
async function loadData() {
  loading.value = true
  try {
    const res = await dashboardApi.get(days.value)
    const d   = res.data
    Object.assign(metrics, d.metrics || {})
    topQueries.value = d.topQueries || []
    hotCategories.value = normalizeCategories(d.hotCategories || [])

    const now = new Date()
    lastUpdateTime.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`

    await nextTick()
    renderTrendChart(d.sessionTrend || [], d.uniqueUserTrend || [])
    renderIntentChart(d.intentDistribution || [])
    renderToolChart(d.toolCalls || [])
    renderGauges()
    renderTakeoverChart(d.humanTakeoverTrend || null)
  } finally {
    loading.value = false
  }
}

function normalizeCategories(categories) {
  const total = categories.reduce((sum, item) => sum + Number(item.cnt || 0), 0)
  return categories.map(item => ({
    name: item.name || '其他',
    pct: total > 0 ? Number((Number(item.cnt || 0) / total * 100).toFixed(1)) : 0,
  }))
}

function showEmptyChart(chart, text = '暂无数据') {
  chart.clear()
  chart.setOption({
    title: {
      text,
      left: 'center',
      top: 'middle',
      textStyle: { color: '#c2c7d0', fontSize: 13, fontWeight: 400 },
    }
  })
}

// ── 会话趋势图 ────────────────────────────────────────────────────────────
function renderTrendChart(sessionTrend, messageTrend) {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  trendChart.clear()
  const dates    = sessionTrend.map(d => d.date_str || d.dateStr)
  const sessions = sessionTrend.map(d => d.cnt)
  const msgDates = messageTrend.map(d => d.date_str || d.dateStr)
  const msgData  = messageTrend.map(d => d.cnt)
  const allDates = [...new Set([...dates, ...msgDates])].sort()
  if (allDates.length === 0) {
    showEmptyChart(trendChart)
    return
  }

  const xData = allDates
  const sData = allDates.map(d => { const i = dates.indexOf(d); return i >= 0 ? sessions[i] : 0 })
  const mData = allDates.map(d => { const i = msgDates.indexOf(d); return i >= 0 ? msgData[i] : 0 })

  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e8ecf0',
      borderWidth: 1,
      textStyle: { color: '#1d2129', fontSize: 12 },
      extraCssText: 'box-shadow:0 4px 16px rgba(0,0,0,.08)',
    },
    legend: {
      data: ['会话总数', '独立用户数'],
      top: 4, left: 0,
      textStyle: { color: '#4e5969', fontSize: 12 },
      icon: 'circle', itemWidth: 8, itemHeight: 8,
    },
    grid: { left: 48, right: 16, bottom: 28, top: 44 },
    xAxis: {
      type: 'category', data: xData,
      axisLabel: { fontSize: 11, color: '#86909c' },
      axisLine: { lineStyle: { color: '#e8ecf0' } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { fontSize: 11, color: '#86909c', formatter: v => v >= 1000 ? (v/1000).toFixed(0)+'K' : v },
      splitLine: { lineStyle: { color: '#f5f7fa', type: 'dashed' } },
      axisLine: { show: false },
    },
    series: [
      {
        name: '会话总数', type: 'line', smooth: 0.3,
        data: sData,
        itemStyle: { color: '#1677ff' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(22,119,255,0.18)' },
              { offset: 1, color: 'rgba(22,119,255,0)' }
            ]
          }
        },
        lineStyle: { width: 2, color: '#1677ff' },
        symbol: 'circle', symbolSize: 5,
        label: { show: true, fontSize: 10, color: '#4e5969', position: 'top' },
      },
      {
        name: '独立用户数', type: 'line', smooth: 0.3,
        data: mData,
        itemStyle: { color: '#86909c' },
        lineStyle: { width: 2, type: 'dashed', color: '#86909c' },
        symbol: 'circle', symbolSize: 5,
        label: { show: true, fontSize: 10, color: '#86909c', position: 'bottom' },
      },
    ]
  })
}

// ── 意图分布饼图 ──────────────────────────────────────────────────────────
function renderIntentChart(intentDist) {
  if (!intentChartRef.value) return
  if (!intentChart) intentChart = echarts.init(intentChartRef.value)
  intentChart.clear()

  if (!intentDist.length) {
    showEmptyChart(intentChart)
    return
  }
  const data = intentDist.map((d, i) => ({
    name: intentMap[d.intent] || d.intent || '未知',
    value: d.cnt,
    itemStyle: { color: intentColors[i % intentColors.length] }
  }))

  intentChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {d}%',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e8ecf0',
      textStyle: { color: '#1d2129', fontSize: 12 },
    },
    legend: {
      orient: 'vertical', right: 6, top: 'center',
      textStyle: { fontSize: 11, color: '#4e5969' },
      icon: 'roundRect', itemWidth: 10, itemHeight: 10, itemGap: 10,
      formatter: name => name.length > 6 ? `${name.slice(0, 6)}…` : name,
    },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['34%', '50%'],
      data,
      label: {
        formatter: '{b}\n{d}%',
        fontSize: 11,
        color: '#4e5969',
      },
      labelLine: { length: 8, length2: 6 },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.15)' },
        label: { show: true, fontSize: 12, fontWeight: 'bold' },
      }
    }]
  })
}

// ── 工具调用柱状图 ────────────────────────────────────────────────────────
function renderToolChart(toolCalls) {
  if (!toolChartRef.value) return
  if (!toolChart) toolChart = echarts.init(toolChartRef.value)
  toolChart.clear()

  if (!toolCalls.length) {
    showEmptyChart(toolChart)
    return
  }
  const names  = toolCalls.map(t => formatToolName(t.tool_name || t.toolName))
  const counts = toolCalls.map(t => t.cnt)

  toolChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e8ecf0',
      textStyle: { color: '#1d2129', fontSize: 12 },
    },
    grid: { left: 92, right: 42, bottom: 20, top: 10 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { fontSize: 10, color: '#86909c', formatter: v => v >= 1000 ? (v/1000).toFixed(1)+'K' : v },
      splitLine: { lineStyle: { color: '#f5f7fa', type: 'dashed' } },
    },
    yAxis: {
      type: 'category', data: names,
      axisLabel: { fontSize: 11, color: '#4e5969', width: 78, overflow: 'truncate' },
      axisTick: { show: false },
      axisLine: { show: false },
    },
    series: [{
      type: 'bar', data: counts,
      itemStyle: { color: '#1677ff', borderRadius: [0, 4, 4, 0] },
      barMaxWidth: 20,
      label: { show: true, position: 'right', fontSize: 11, color: '#4e5969' },
    }]
  })
}

// ── 知识库命中率仪表盘 ────────────────────────────────────────────────────
function renderGauges() {
  const hitValue = Number(metrics.knowledgeHitRate || 0)
  const citeValue = Number(metrics.citationRate || 0)
  const gaugeOpts = (value, color) => ({
    series: [{
      type: 'gauge',
      radius: '95%',
      startAngle: 205,
      endAngle: -25,
      min: 0, max: 100,
      progress: {
        show: true,
        width: 9,
        itemStyle: { color }
      },
      axisLine: { lineStyle: { width: 9, color: [[1, '#eef0f4']] } },
      axisTick:  { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      pointer:   { show: false },
      detail:    { show: false },
      data: [{ value }]
    }]
  })

  if (hitChartRef.value) {
    if (!hitChart) hitChart = echarts.init(hitChartRef.value)
    hitChart.clear()
    hitChart.setOption(gaugeOpts(hitValue, '#1677ff'))
  }
  if (citeChartRef.value) {
    if (!citeChart) citeChart = echarts.init(citeChartRef.value)
    citeChart.clear()
    citeChart.setOption(gaugeOpts(citeValue, '#17a855'))
  }
}

// ── 人工接管趋势图 ────────────────────────────────────────────────────────
function renderTakeoverChart(apiData) {
  if (!takeoverChartRef.value) return
  if (!takeoverChart) takeoverChart = echarts.init(takeoverChartRef.value)
  takeoverChart.clear()

  if (!apiData || apiData.length === 0) {
    showEmptyChart(takeoverChart)
    return
  }

  const xData = apiData.map(d => d.date_str || d.dateStr)
  const cData = apiData.map(d => d.cnt)
  const rData = apiData.map(d => d.rate)
  const maxCount = Math.max(...cData, 0)
  const maxRate = Math.max(...rData, 0)
  const countMax = Math.max(5, Math.ceil((maxCount + 1) / 2) * 2)
  const rateMax = Math.max(10, Math.ceil((maxRate + 5) / 10) * 10)

  takeoverChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#e8ecf0',
      textStyle: { color: '#1d2129', fontSize: 12 },
    },
    legend: {
      data: ['转人工会话数', '转人工率'],
      top: 4, left: 0,
      textStyle: { color: '#4e5969', fontSize: 11 },
      icon: 'circle', itemWidth: 8, itemHeight: 8,
    },
    grid: { left: 42, right: 48, bottom: 30, top: 44 },
    xAxis: {
      type: 'category', data: xData,
      axisLabel: { fontSize: 10, color: '#86909c', hideOverlap: true },
      axisLine: { lineStyle: { color: '#e8ecf0' } },
      axisTick: { show: false },
    },
    yAxis: [
      {
        type: 'value',
        name: '',
        min: 0,
        max: countMax,
        minInterval: 1,
        axisLabel: { fontSize: 10, color: '#86909c' },
        splitLine: { lineStyle: { color: '#f5f7fa', type: 'dashed' } },
        axisLine: { show: false },
      },
      {
        type: 'value',
        name: '',
        min: 0, max: rateMax,
        axisLabel: { fontSize: 10, color: '#86909c', formatter: '{value}%' },
        splitLine: { show: false },
        axisLine: { show: false },
      },
    ],
    series: [
      {
        name: '转人工会话数', type: 'bar',
        yAxisIndex: 0, data: cData,
        itemStyle: { color: '#8b6fcb', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 18,
        label: { show: true, fontSize: 10, color: '#7b5ea7', position: 'top' },
      },
      {
        name: '转人工率', type: 'line', smooth: 0.3,
        yAxisIndex: 1, data: rData,
        itemStyle: { color: '#fa8c16' },
        lineStyle: { width: 2, color: '#fa8c16' },
        symbol: 'circle', symbolSize: 5,
        label: { show: false },
      },
    ]
  })
}

// ── 图表自适应 ────────────────────────────────────────────────────────────
function resizeCharts() {
  trendChart?.resize()
  intentChart?.resize()
  toolChart?.resize()
  hitChart?.resize()
  citeChart?.resize()
  takeoverChart?.resize()
}
</script>

<style scoped>
.dashboard-page {
  padding: 20px 24px;
  height: 100%;
  overflow-y: auto;
  background: #f5f7fa;
  box-sizing: border-box;
}

/* ── Header ── */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;
}
.page-title    { margin: 0 0 4px; font-size: 20px; font-weight: 700; color: #1d2129; }
.page-subtitle { margin: 0; font-size: 13px; color: #86909c; }
.header-right  { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

.date-range-label {
  font-size: 13px;
  color: #4e5969;
  font-variant-numeric: tabular-nums;
}

.days-radio :deep(.el-radio-button__inner) {
  padding: 5px 12px;
  font-size: 12px;
}

/* ── Metrics grid ── */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.metric-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
  border: 1px solid #f0f2f5;
  transition: box-shadow .15s;
}
.metric-card:hover { box-shadow: 0 3px 10px rgba(0,0,0,.07); }
.mc-icon {
  width: 44px; height: 44px;
  border-radius: 11px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.mc-body    { min-width: 0; flex: 1; }
.mc-label   { font-size: 12px; color: #86909c; margin-bottom: 2px; white-space: nowrap; }
.mc-value   { font-size: 22px; font-weight: 700; color: #1d2129; line-height: 1.2; }
.mc-trend   { font-size: 11px; display: flex; align-items: center; gap: 2px; color: #86909c; margin-top: 3px; }
.mc-trend.up      { color: #17a855; }
.mc-trend.down    { color: #ff4d4f; }
.mc-trend.neutral { color: #4e5969; }

/* ── Charts rows ── */
.charts-row {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
}
.row1 { grid-template-columns: 2fr 1.1fr 1fr; }
.row2 { grid-template-columns: 0.85fr 0.9fr 1.2fr 1fr; }

/* ── Card base ── */
.chart-card {
  border-radius: 12px !important;
  border-color: #f0f2f5 !important;
  background: #fff;
  overflow: hidden;
}
.chart-card :deep(.el-card__header) {
  padding: 10px 14px;
  border-bottom: 1px solid #f5f7fa;
  background: #fff;
}
.chart-card :deep(.el-card__body) {
  padding: 10px 14px;
  background: #fff;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 5px;
}
.card-title { font-size: 13px; font-weight: 600; color: #1d2129; }
.card-more  { font-size: 12px; color: #1677ff; cursor: pointer; white-space: nowrap; }
.card-more:hover { text-decoration: underline; }

.chart-box    { width: 100%; }
.h220         { height: 220px; }
.h200         { height: 200px; }

/* ── Category list ── */
.category-list { padding: 2px 0; }
.cat-header-row {
  display: flex;
  font-size: 11px;
  color: #86909c;
  margin-bottom: 6px;
}
.cat-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
}
.cat-name {
  font-size: 12px;
  color: #4e5969;
  width: 60px;
  flex-shrink: 0;
  white-space: nowrap;
}
.cat-bar-wrap {
  flex: 1;
  height: 6px;
  background: #f0f2f5;
  border-radius: 3px;
  overflow: hidden;
}
.cat-bar-fill {
  height: 100%;
  background: #1677ff;
  border-radius: 3px;
  transition: width .4s ease;
}
.cat-pct { font-size: 12px; color: #4e5969; width: 38px; text-align: right; flex-shrink: 0; }

/* ── Hit rate gauges ── */
.hit-rate-row {
  display: flex;
  align-items: center;
  padding: 4px 0;
}
.hit-gauge-wrap { flex: 1; text-align: center; }
.hit-divider    { width: 1px; background: #f0f2f5; height: 148px; flex-shrink: 0; }
.hit-gauge-title { font-size: 12px; color: #86909c; margin-bottom: 2px; }
.gauge-box       { height: 78px; }
.hit-big-val {
  font-size: 22px; font-weight: 700; color: #1d2129;
  margin: -10px 0 2px; line-height: 1;
}
.hit-trend {
  font-size: 11px;
  display: flex; align-items: center; justify-content: center;
  gap: 2px; color: #86909c;
}
.hit-trend.up { color: #17a855; }
.hit-hint { font-size: 11px; color: #86909c; margin-top: 4px; }

/* ── TOP10 table ── */
.top10-head-row {
  display: grid;
  grid-template-columns: 28px 1fr 44px;
  gap: 6px;
  font-size: 11px;
  color: #86909c;
  padding: 0 4px 6px;
  border-bottom: 1px solid #f5f7fa;
}
.top-query-list { display: flex; flex-direction: column; gap: 1px; padding-top: 3px; }
.top-item {
  display: grid;
  grid-template-columns: 28px 1fr 44px;
  gap: 6px;
  align-items: center;
  padding: 5px 4px;
  border-radius: 6px;
  transition: background .12s;
}
.top-item:hover { background: #f5f7fa; }
.rank-badge {
  width: 20px; height: 20px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700;
  background: #f0f2f5; color: #86909c;
}
.rank-1 { background: #fa8c16; color: #fff; }
.rank-2 { background: #8c8c8c; color: #fff; }
.rank-3 { background: #cd7c3b; color: #fff; }
.query-text { font-size: 12px; color: #4e5969; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.query-cnt  { font-size: 12px; color: #1d2129; font-weight: 500; text-align: right; }

/* ── Footer ── */
.page-footer {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #86909c;
  padding: 10px 0 4px;
}

/* ── Responsive ── */
@media (max-width: 1200px) {
  .metrics-grid { grid-template-columns: repeat(3, 1fr); }
  .row1         { grid-template-columns: 1.5fr 1fr; }
  .row1 .category-card { display: none; }
  .row2         { grid-template-columns: 1fr 1fr; }
}
</style>
