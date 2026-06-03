<template>
  <div class="tickets-page">
    <!-- ── 顶部横幅 ── -->
    <div class="page-banner">
      <div class="banner-robot">
        <svg width="90" height="90" viewBox="0 0 90 90" fill="none">
          <circle cx="45" cy="45" r="42" fill="#c7d9ff" opacity="0.5"/>
          <rect x="20" y="26" width="50" height="40" rx="14" fill="#1677ff" opacity="0.9"/>
          <circle cx="33" cy="43" r="6.5" fill="white"/>
          <circle cx="57" cy="43" r="6.5" fill="white"/>
          <circle cx="34.5" cy="43" r="2.8" fill="#1677ff"/>
          <circle cx="58.5" cy="43" r="2.8" fill="#1677ff"/>
          <rect x="30" y="53" width="30" height="6" rx="3" fill="white" opacity="0.9"/>
          <rect x="8" y="34" width="9" height="16" rx="4.5" fill="#1677ff" opacity="0.4"/>
          <rect x="73" y="34" width="9" height="16" rx="4.5" fill="#1677ff" opacity="0.4"/>
          <rect x="32" y="10" width="11" height="16" rx="5.5" fill="#1677ff" opacity="0.55"/>
          <rect x="47" y="10" width="11" height="16" rx="5.5" fill="#1677ff" opacity="0.55"/>
        </svg>
      </div>
      <div class="banner-content">
        <h2 class="banner-title">老师工作台</h2>
        <div class="banner-sub">接管复杂会话，协同解决学生问题</div>
        <div class="banner-desc">与 AI 助理协同，为学生提供更专业、更有温度的服务。</div>
      </div>
    </div>

    <!-- ── 统计卡片 ── -->
    <div class="stats-bar">
      <div class="stat-card" @click="filterByStatus('PENDING')">
        <div class="sc-icon blue"><el-icon :size="22"><Bell /></el-icon></div>
        <div class="sc-body">
          <div class="sc-label">待回复会话</div>
          <div class="sc-num">{{ statsMap.PENDING ?? 0 }}</div>
          <div class="sc-trend up">
            <el-icon :size="11"><Top /></el-icon>
            最久等待 32 分钟
          </div>
        </div>
      </div>
      <div class="stat-card" @click="filterByStatus('HANDLING')">
        <div class="sc-icon yellow"><el-icon :size="22"><Timer /></el-icon></div>
        <div class="sc-body">
          <div class="sc-label">处理中</div>
          <div class="sc-num">{{ statsMap.HANDLING ?? 0 }}</div>
          <div class="sc-trend">我负责 {{ Math.min(statsMap.HANDLING ?? 0, 4) }} 个会话</div>
        </div>
      </div>
      <div class="stat-card" @click="filterByStatus('RESOLVED')">
        <div class="sc-icon green"><el-icon :size="22"><CircleCheck /></el-icon></div>
        <div class="sc-body">
          <div class="sc-label">今日已解决</div>
          <div class="sc-num">{{ statsMap.RESOLVED ?? 0 }}</div>
          <div class="sc-trend up">
            <el-icon :size="11"><Top /></el-icon>
            满意度 98%
          </div>
        </div>
      </div>
      <div class="stat-card">
        <div class="sc-icon purple"><el-icon :size="22"><Clock /></el-icon></div>
        <div class="sc-body">
          <div class="sc-label">平均处理时长</div>
          <div class="sc-num">18 <span class="sc-unit">分钟</span></div>
          <div class="sc-trend down">目标 20 分钟以内</div>
        </div>
      </div>
    </div>

    <!-- ── 主体区域 ── -->
    <div class="main-area">
      <!-- 左侧队列 -->
      <div class="queue-panel">
        <div class="queue-tabs-row">
          <button
            v-for="tab in tabs"
            :key="tab.val"
            class="q-tab"
            :class="{ active: activeTab === tab.val }"
            @click="activeTab = tab.val; loadTickets()"
          >
            {{ tab.label }}
            <span v-if="tab.count !== undefined" class="q-tab-badge">{{ tab.count }}</span>
          </button>
        </div>
        <div class="queue-search-row">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索学生姓名 / 会话ID / 问题关键词"
            :prefix-icon="Search"
            size="small"
            clearable
          />
        </div>
        <div class="queue-filter-row">
          <el-select v-model="urgencyFilter" placeholder="全部紧急度" clearable size="small" @change="loadTickets">
            <el-option label="紧急" value="HIGH" />
            <el-option label="普通" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
          <el-select placeholder="全部问题类型" clearable size="small" style="flex:1">
            <el-option label="成绩申诉" value="grade" />
            <el-option label="学籍问题" value="status" />
            <el-option label="选课问题" value="course" />
            <el-option label="奖助学金" value="scholarship" />
          </el-select>
          <el-button size="small" circle text><el-icon><Operation /></el-icon></el-button>
        </div>
        <div class="queue-list" v-loading="listLoading">
          <el-empty v-if="filteredTickets.length === 0" description="暂无工单" :image-size="56" />
          <div
            v-for="t in filteredTickets"
            :key="t.id"
            class="queue-item"
            :class="{ active: selectedTicket?.id === t.id }"
            @click="selectTicket(t)"
          >
            <div class="qi-row1">
              <el-tag size="small" :type="urgencyTagType(t.urgency)" class="qi-urgency-tag" effect="dark">
                {{ urgencyLabel(t.urgency) }}
              </el-tag>
              <span class="qi-type-tag" v-if="t.issueType">{{ t.issueType }}</span>
              <span class="qi-wait">{{ formatTime(t.createdAt) }}</span>
            </div>
            <div class="qi-student">{{ t.studentName || '同学' }}</div>
            <div class="qi-id">会话ID：{{ t.ticketNo }}</div>
            <div class="qi-subject">{{ t.subject || '暂无标题' }}</div>
            <div class="qi-status-row">
              <span class="qi-status" :class="'qs-' + (t.status || 'PENDING').toLowerCase()">
                {{ statusLabel(t.status) }}
              </span>
            </div>
          </div>
        </div>
        <div class="queue-footer">
          <span>共 {{ tickets.length }} 条会话</span>
          <div class="ws-indicator">
            <span class="ws-dot" :class="wsConnected ? 'on' : 'off'" />
            {{ wsConnected ? '实时' : '离线' }}
          </div>
        </div>
      </div>

      <!-- 中央会话区域 -->
      <div class="detail-panel" v-if="selectedTicket">
        <!-- 会话头部 -->
        <div class="detail-head">
          <div class="dh-left">
            <span class="dh-no">会话：{{ selectedTicket.ticketNo }}</span>
            <el-tag size="small" :type="urgencyTagType(selectedTicket.urgency)" effect="dark">
              {{ urgencyLabel(selectedTicket.urgency) }}
            </el-tag>
            <span class="dh-user">{{ selectedTicket.studentName || '同学' }}</span>
            <span class="dh-uid">({{ selectedTicket.studentId || '—' }})</span>
            <el-tag size="small" type="warning" effect="plain" v-if="selectedTicket.issueType">
              {{ selectedTicket.issueType }}
            </el-tag>
            <span class="dh-wait">
              <el-icon :size="11"><Clock /></el-icon>
              等待时间：{{ formatTime(selectedTicket.createdAt) }}
            </span>
          </div>
          <div class="dh-actions">
            <el-button
              v-if="['PENDING','HANDLING'].includes(selectedTicket.status)"
              type="success" size="small"
              @click="resolveTicket"
              :loading="actionLoading"
            >标记解决</el-button>
            <el-dropdown trigger="click" v-if="['HANDLING','RESOLVED'].includes(selectedTicket.status)">
              <el-button size="small">更多操作 <el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="closeTicket">关闭工单</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <!-- 对话记录 -->
            <div class="conv-title">学生、AI 与老师对话记录</div>
        <div class="msg-area" ref="msgAreaRef">
          <div v-if="msgLoading" class="msg-placeholder">
            <el-icon class="spin"><Loading /></el-icon> 加载会话记录...
          </div>
          <div v-else-if="messages.length === 0" class="msg-placeholder">暂无会话记录</div>
          <div v-else>
            <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
              <el-avatar :size="30" :style="avatarStyle(m.role)">
                {{ avatarText(m.role) }}
              </el-avatar>
              <div class="msg-bubble-wrap">
                <div class="msg-sender">
                  {{ senderLabel(m.role) }}
                  <span class="msg-ts">{{ formatDateTime(m.createdAt) }}</span>
                </div>
                <div class="msg-bubble" :class="m.role">
                  <div class="msg-content">{{ m.content }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- AI 分析摘要 -->
        <div v-if="selectedTicket.aiSummary" class="ai-summary-box">
          <div class="ai-sum-head" @click="summaryExpanded = !summaryExpanded">
            <el-icon color="#1677ff" :size="14"><MagicStick /></el-icon>
            <span class="ai-sum-label">AI 分析摘要</span>
            <el-icon :size="12" class="sum-toggle" :class="{ expanded: summaryExpanded }">
              <ArrowDown />
            </el-icon>
          </div>
          <div v-show="summaryExpanded" class="ai-sum-body">{{ selectedTicket.aiSummary }}</div>
        </div>

        <!-- 回复输入 -->
        <div class="reply-area">
          <div v-if="quickReplyVisible" class="quick-reply-panel">
            <button
              v-for="item in quickReplies"
              :key="item.title"
              class="quick-reply-chip"
              type="button"
              @click="applyQuickReply(item.content)"
            >
              <span class="qr-title">{{ item.title }}</span>
              <span class="qr-content">{{ item.content }}</span>
            </button>
          </div>
          <el-input
            v-model="replyText"
            type="textarea"
            :rows="2"
            placeholder="输入回复内容，Enter 发送，Shift + Enter 换行"
            resize="none"
            @keydown.enter.exact.prevent="sendReply"
            class="reply-input"
          />
          <div class="reply-actions">
            <el-button size="small" plain @click="quickReplyVisible = !quickReplyVisible">
              <el-icon><Comment /></el-icon> 快捷回复
            </el-button>
            <div style="flex:1" />
            <el-button
              type="primary" size="small"
              :disabled="!replyText.trim()"
              @click="sendReply"
            >发送</el-button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div class="detail-panel empty-panel" v-else>
        <el-empty description="从左侧选择一个会话查看详情" :image-size="80" />
      </div>

      <!-- 右侧信息栏 -->
      <div class="info-panel" v-if="selectedTicket">
        <!-- 会话信息 -->
        <div class="info-section">
          <div class="info-section-head">
            <span>会话信息</span>
            <span class="info-badge pending">{{ statusLabel(selectedTicket.status) }}</span>
          </div>
          <div class="info-row"><span class="ik">会话 ID</span><span class="iv">{{ selectedTicket.ticketNo }}</span></div>
          <div class="info-row"><span class="ik">发起时间</span><span class="iv">{{ formatDateTime(selectedTicket.createdAt) }}</span></div>
          <div class="info-row"><span class="ik">等待时长</span><span class="iv">{{ formatTime(selectedTicket.createdAt) }}</span></div>
          <div class="info-row" v-if="selectedTicket.issueType">
            <span class="ik">问题类型</span>
            <span class="iv">{{ selectedTicket.issueType }}</span>
          </div>
          <div class="info-row">
            <span class="ik">紧急程度</span>
            <el-tag size="small" :type="urgencyTagType(selectedTicket.urgency)" effect="plain">
              {{ urgencyLabel(selectedTicket.urgency) }}
            </el-tag>
          </div>
        </div>

        <!-- 风险评估 -->
        <div class="info-section">
          <div class="info-section-head"><span>风险评估</span></div>
          <div class="risk-badge" :class="'risk-' + (selectedTicket.urgency || 'LOW').toLowerCase()">
            <el-icon :size="14"><Warning /></el-icon>
            {{ riskLevel(selectedTicket.urgency) }}
          </div>
          <div class="risk-desc">{{ riskDesc(selectedTicket.urgency) }}</div>
          <div class="info-link mt-8" style="font-size:12px">查看详情 ></div>
        </div>

        <!-- 用户评分 -->
        <div class="info-section" v-if="selectedTicket.rating">
          <div class="info-section-head"><span>用户评分</span></div>
          <el-rate :model-value="selectedTicket.rating" disabled />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 【导读】老师工作台
 * - loadTickets / selectTicket：左侧队列与中间对话记录（GET /tickets/{id}/messages）
 * - sendReply：POST /tickets/{id}/reply，需 teacher001 等教师账号登录
 * - connectWs：STOMP 订阅 /topic/tickets 刷新列表（可选）
 */
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bell, Timer, CircleCheck, Clock, MagicStick, Loading,
  ArrowDown, Search, Operation, Top, Comment, Warning
} from '@element-plus/icons-vue'
import { ticketsApi } from '../api/tickets'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

const tickets        = ref([])
const selectedTicket = ref(null)
const messages       = ref([])
const statsMap       = reactive({ PENDING: 0, HANDLING: 0, RESOLVED: 0, CLOSED: 0 })
const listLoading    = ref(false)
const msgLoading     = ref(false)
const actionLoading  = ref(false)
const activeTab      = ref('all')
const urgencyFilter  = ref('')
const wsConnected    = ref(false)
const msgAreaRef     = ref(null)
const replyText      = ref('')
const searchKeyword  = ref('')
const summaryExpanded = ref(true)
const quickReplyVisible = ref(false)

const quickReplies = [
  { title: '已收到', content: '同学你好，老师已收到你的问题，我会先核对相关信息后尽快回复你。' },
  { title: '补充材料', content: '请你补充一下学号、所在学院、问题发生时间，以及相关截图或证明材料，方便进一步核实。' },
  { title: '办理路径', content: '这个问题建议先按学校规定流程提交申请，老师会同步关注处理进度，如有异常会继续协助你。' },
  { title: '联系部门', content: '该事项需要由对应职能部门最终确认，建议你同时联系相关办公室，老师这边也会协助跟进。' },
  { title: '已处理', content: '你的问题已处理完成，请你刷新页面或稍后再次查看结果。如仍有异常，可以继续在这里留言。' },
  { title: '安抚说明', content: '理解你的着急，我们会尽快核实并给出明确答复，请先不要重复提交，避免影响处理进度。' },
]

let stompClient = null

const tabs = computed(() => [
  { val: 'all',      label: '全部',   count: undefined },
  { val: 'PENDING',  label: '待回复', count: statsMap.PENDING },
  { val: 'HANDLING', label: '处理中', count: statsMap.HANDLING },
  { val: 'RESOLVED', label: '已完成', count: undefined },
])

const filteredTickets = computed(() => {
  if (!searchKeyword.value) return tickets.value
  const kw = searchKeyword.value.toLowerCase()
  return tickets.value.filter(t =>
    (t.ticketNo || '').toLowerCase().includes(kw) ||
    (t.subject  || '').toLowerCase().includes(kw) ||
    (t.studentName || '').toLowerCase().includes(kw)
  )
})

onMounted(() => { loadStats(); loadTickets(); connectWs() })
onUnmounted(() => { stompClient?.deactivate() })

async function loadStats() {
  try {
    const res = await ticketsApi.stats()
    Object.assign(statsMap, res.data || {})
  } catch {}
}

async function loadTickets() {
  listLoading.value = true
  try {
    const params = {}
    if (activeTab.value !== 'all') params.status = activeTab.value
    if (urgencyFilter.value) params.urgency = urgencyFilter.value
    const res = await ticketsApi.list(params)
    tickets.value = res.data || []
  } finally {
    listLoading.value = false
  }
}

async function selectTicket(ticket) {
  selectedTicket.value = ticket
  messages.value = []
  msgLoading.value = true
  try {
    const res = await ticketsApi.getMessages(ticket.id)
    messages.value = res.data || []
    await nextTick()
    if (msgAreaRef.value) msgAreaRef.value.scrollTop = msgAreaRef.value.scrollHeight
  } finally {
    msgLoading.value = false
  }
}

async function resolveTicket() {
  actionLoading.value = true
  try {
    await ticketsApi.resolve(selectedTicket.value.id)
    selectedTicket.value.status = 'RESOLVED'
    ElMessage.success('已标记为解决')
    loadStats(); loadTickets()
  } finally { actionLoading.value = false }
}

async function closeTicket() {
  actionLoading.value = true
  try {
    await ticketsApi.close(selectedTicket.value.id)
    selectedTicket.value.status = 'CLOSED'
    ElMessage.success('工单已关闭')
    loadStats(); loadTickets()
  } finally { actionLoading.value = false }
}

function filterByStatus(status) { activeTab.value = status; loadTickets() }

async function sendReply() {
  if (!replyText.value.trim()) return
  const content = replyText.value.trim()
  actionLoading.value = true
  try {
    await ticketsApi.reply(selectedTicket.value.id, content)
    selectedTicket.value.status = 'HANDLING'
    replyText.value = ''
    ElMessage.success('回复已发送')
    await selectTicket(selectedTicket.value)
    loadStats(); loadTickets()
  } finally {
    actionLoading.value = false
  }
}

function applyQuickReply(content) {
  if (!replyText.value.trim()) {
    replyText.value = content
  } else {
    replyText.value = `${replyText.value.trim()}\n${content}`
  }
}

function connectWs() {
  const token = authStore.token
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 5000,
    onConnect: () => {
      wsConnected.value = true
      stompClient.subscribe('/topic/tickets', (msg) => {
        try {
          const data = JSON.parse(msg.body)
          if (data.ticketId) { loadStats(); loadTickets() }
        } catch {}
      })
    },
    onDisconnect: () => { wsConnected.value = false },
    onStompError:  () => { wsConnected.value = false },
  })
  stompClient.activate()
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts), now = new Date(), diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时`
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function formatDateTime(ts) {
  if (!ts) return '—'
  return new Date(ts).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function urgencyLabel(u) { return { HIGH: '高', MEDIUM: '中', LOW: '低' }[u] || u }
function urgencyTagType(u) { return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[u] || '' }
function statusLabel(s) { return { PENDING: '待回复', HANDLING: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }[s] || s }
function avatarStyle(role) {
  if (role === 'user') return { background: '#1677ff', color: 'white', fontSize: '12px' }
  if (role === 'teacher') return { background: '#fa8c16', color: 'white', fontSize: '12px' }
  return { background: '#0d9488', color: 'white', fontSize: '12px' }
}
function avatarText(role) {
  if (role === 'user') return selectedTicket.value?.studentName?.charAt(0) || 'S'
  if (role === 'teacher') return '师'
  return 'AI'
}
function senderLabel(role) {
  if (role === 'user') return selectedTicket.value?.studentName || '学生'
  if (role === 'teacher') return '老师'
  return 'AI 助理'
}
function riskLevel(u) { return { HIGH: '高风险', MEDIUM: '中等风险', LOW: '低风险' }[u] || '未知' }
function riskDesc(u) {
  return {
    HIGH:   '用户情绪激动，需立即处理，避免升级',
    MEDIUM: '一般问题，正常响应时间内处理即可',
    LOW:    '低优先级，可按队列顺序处理',
  }[u] || ''
}
</script>

<style scoped>
.tickets-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #f5f7fa;
}

/* ── Banner ── */
.page-banner {
  flex-shrink: 0;
  background: linear-gradient(135deg, #eaf1ff 0%, #d4e4ff 60%, #e8f5ff 100%);
  padding: 18px 28px;
  display: flex;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid #e0eaff;
}
.banner-robot { flex-shrink: 0; }
.banner-title { font-size: 20px; font-weight: 700; color: #1d2129; margin: 0 0 4px; }
.banner-sub   { font-size: 14px; color: #4e5969; margin-bottom: 2px; }
.banner-desc  { font-size: 12px; color: #86909c; }

/* ── Stat cards ── */
.stats-bar {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 14px 20px;
  background: #f5f7fa;
}
.stat-card {
  background: white;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  transition: all .15s;
  box-shadow: 0 1px 3px rgba(0,0,0,.05);
}
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.08); }
.sc-icon {
  width: 48px; height: 48px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.sc-icon.blue   { background: #e6f0ff; color: #1677ff; }
.sc-icon.yellow { background: #fff7e6; color: #fa8c16; }
.sc-icon.green  { background: #f0fdf4; color: #17a855; }
.sc-icon.purple { background: #f5f0ff; color: #7b5ea7; }
.sc-label { font-size: 12px; color: #86909c; margin-bottom: 2px; }
.sc-num   { font-size: 26px; font-weight: 700; color: #1d2129; line-height: 1.2; }
.sc-unit  { font-size: 14px; font-weight: 400; }
.sc-trend { font-size: 11px; color: #86909c; display: flex; align-items: center; gap: 2px; }
.sc-trend.up   { color: #fa8c16; }
.sc-trend.down { color: #17a855; }

/* ── Main area ── */
.main-area {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 0;
  margin: 0 16px 16px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e8ecf0;
  background: white;
}

/* ── Queue panel ── */
.queue-panel {
  width: 290px;
  flex-shrink: 0;
  border-right: 1px solid #f0f2f5;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
}
.queue-tabs-row {
  display: flex;
  gap: 2px;
}
.q-tab {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 12px;
  border-radius: 8px 8px 0 0;
  border: none;
  background: transparent;
  font-size: 13px;
  color: #86909c;
  cursor: pointer;
  font-weight: 500;
  transition: all .15s;
  border-bottom: 2px solid transparent;
}
.q-tab:hover { color: #1677ff; }
.q-tab.active { color: #1677ff; border-bottom-color: #1677ff; }
.q-tab-badge {
  background: #ff4d4f;
  color: white;
  font-size: 10px;
  border-radius: 10px;
  padding: 0 5px;
  min-width: 16px;
  line-height: 16px;
  text-align: center;
}
.queue-search-row { padding: 10px 12px 6px; }
.queue-filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px 8px;
}
.queue-filter-row .el-select { flex: 1; }
.queue-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
}
.queue-list::-webkit-scrollbar { width: 4px; }
.queue-list::-webkit-scrollbar-thumb { background: #e0e3e8; border-radius: 2px; }

.queue-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background .15s;
  margin-bottom: 4px;
  border: 1px solid transparent;
}
.queue-item:hover { background: #f0f5ff; }
.queue-item.active { background: #f0f5ff; border-color: #c7d9ff; }
.qi-row1 {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 4px;
}
.qi-urgency-tag { flex-shrink: 0; }
.qi-type-tag {
  font-size: 11px;
  color: #fa8c16;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  padding: 0 5px;
}
.qi-wait { margin-left: auto; font-size: 11px; color: #86909c; }
.qi-student { font-size: 13px; font-weight: 600; color: #1d2129; margin-bottom: 2px; }
.qi-id { font-size: 11px; color: #86909c; margin-bottom: 3px; }
.qi-subject {
  font-size: 12px;
  color: #4e5969;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 5px;
}
.qi-status-row { display: flex; }
.qi-status {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 7px;
  border-radius: 10px;
}
.qs-pending  { background: #fff7e6; color: #fa8c16; }
.qs-handling { background: #e6f4ff; color: #1677ff; }
.qs-resolved { background: #f0fdf4; color: #17a855; }
.qs-closed   { background: #f5f7fa; color: #86909c; }

.queue-footer {
  padding: 8px 12px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #86909c;
}
.ws-indicator { display: flex; align-items: center; gap: 4px; }
.ws-dot { width: 6px; height: 6px; border-radius: 50%; }
.ws-dot.on  { background: #17a855; }
.ws-dot.off { background: #ef4444; }

/* ── Detail panel ── */
.detail-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  border-right: 1px solid #f0f2f5;
}
.empty-panel { align-items: center; justify-content: center; }

.detail-head {
  flex-shrink: 0;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.dh-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dh-no   { font-size: 12px; color: #86909c; }
.dh-user { font-size: 13px; font-weight: 600; color: #1d2129; }
.dh-uid  { font-size: 12px; color: #86909c; }
.dh-wait { font-size: 12px; color: #fa8c16; display: flex; align-items: center; gap: 3px; }
.dh-actions { display: flex; gap: 8px; flex-shrink: 0; }

.conv-title { font-size: 12px; color: #86909c; padding: 10px 16px 0; }
.msg-area {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg-area::-webkit-scrollbar { width: 4px; }
.msg-area::-webkit-scrollbar-thumb { background: #e0e3e8; border-radius: 2px; }
.msg-placeholder { text-align: center; color: #86909c; font-size: 13px; padding: 40px 0; display: flex; align-items: center; justify-content: center; gap: 6px; }
.msg-row { display: flex; align-items: flex-start; gap: 8px; }
.msg-row.user { flex-direction: row-reverse; }
.msg-bubble-wrap { max-width: 72%; }
.msg-sender { font-size: 12px; color: #86909c; margin-bottom: 4px; }
.msg-row.user .msg-sender { text-align: right; }
.msg-ts { margin-left: 8px; font-size: 11px; color: #c2c7d0; }
.msg-bubble { border-radius: 10px; padding: 10px 14px; font-size: 13px; line-height: 1.6; }
.msg-bubble.user { background: #1677ff; color: white; border-radius: 14px 14px 4px 14px; }
.msg-bubble.assistant { background: #f5f7fa; color: #1d2129; border-radius: 4px 14px 14px 14px; }
.msg-bubble.teacher { background: #fff7e6; color: #1d2129; border: 1px solid #ffe7ba; border-radius: 4px 14px 14px 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.ai-summary-box {
  flex-shrink: 0;
  margin: 0 16px;
  border: 1px solid #d4e4ff;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 8px;
}
.ai-sum-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f0f5ff;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #1677ff;
  user-select: none;
}
.ai-sum-label { flex: 1; }
.sum-toggle { color: #86909c; transition: transform .2s; }
.sum-toggle.expanded { transform: rotate(180deg); }
.ai-sum-body { padding: 10px 12px; font-size: 12px; color: #4e5969; line-height: 1.7; background: white; }

.reply-area {
  flex-shrink: 0;
  padding: 10px 16px 12px;
  border-top: 1px solid #f0f2f5;
}
.quick-reply-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}
.quick-reply-chip {
  min-width: 0;
  text-align: left;
  border: 1px solid #e5eaf3;
  background: #fafcff;
  border-radius: 8px;
  padding: 7px 9px;
  cursor: pointer;
  transition: all .15s;
}
.quick-reply-chip:hover {
  border-color: #9ec5ff;
  background: #f0f7ff;
}
.qr-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #1677ff;
  margin-bottom: 2px;
}
.qr-content {
  display: block;
  font-size: 11px;
  color: #4e5969;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reply-input :deep(.el-textarea__inner) { font-size: 13px; border-radius: 8px; }
.reply-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

/* ── Info panel ── */
.info-panel {
  width: 240px;
  flex-shrink: 0;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.info-panel::-webkit-scrollbar { width: 3px; }
.info-panel::-webkit-scrollbar-thumb { background: #e0e3e8; border-radius: 2px; }
.info-section {
  padding-bottom: 14px;
  margin-bottom: 6px;
  border-bottom: 1px solid #f5f7fa;
}
.info-section:last-child { border-bottom: none; }
.info-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
}
.info-badge {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 10px;
}
.info-badge.pending { background: #fff7e6; color: #fa8c16; }
.info-link { font-size: 12px; color: #1677ff; cursor: pointer; }
.info-link:hover { text-decoration: underline; }
.mt-8 { margin-top: 8px; }
.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed #f5f7fa;
}
.info-row:last-child { border-bottom: none; }
.ik { font-size: 12px; color: #86909c; min-width: 55px; }
.iv { font-size: 12px; color: #4e5969; flex: 1; }
.risk-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}
.risk-high   { background: #fff2f0; color: #ff4d4f; border: 1px solid #ffccc7; }
.risk-medium { background: #fffbe6; color: #fa8c16; border: 1px solid #ffe7ba; }
.risk-low    { background: #f0fdf4; color: #17a855; border: 1px solid #d9f7be; }
.risk-desc   { font-size: 11px; color: #86909c; line-height: 1.5; }
</style>
