<template>
  <div class="chat-layout">
    <!-- ── 中央聊天区 ── -->
    <div class="chat-main">
      <!-- 欢迎横幅（无消息时展示） -->
      <div v-if="messages.length === 0" class="welcome-wrap">
        <div class="welcome-banner">
          <div class="welcome-robot">
            <svg width="120" height="120" viewBox="0 0 120 120" fill="none">
              <circle cx="60" cy="60" r="56" fill="#e6f0ff" opacity="0.8"/>
              <circle cx="60" cy="60" r="44" fill="#cce0ff" opacity="0.6"/>
              <rect x="30" y="38" width="60" height="50" rx="18" fill="#1677ff"/>
              <circle cx="44" cy="58" r="8" fill="white"/>
              <circle cx="76" cy="58" r="8" fill="white"/>
              <circle cx="46" cy="58" r="3.5" fill="#1677ff"/>
              <circle cx="78" cy="58" r="3.5" fill="#1677ff"/>
              <rect x="40" y="71" width="40" height="7" rx="3.5" fill="white" opacity="0.9"/>
              <rect x="14" y="46" width="11" height="22" rx="5.5" fill="#1677ff" opacity="0.4"/>
              <rect x="95" y="46" width="11" height="22" rx="5.5" fill="#1677ff" opacity="0.4"/>
              <rect x="43" y="18" width="13" height="20" rx="6.5" fill="#1677ff" opacity="0.55"/>
              <rect x="64" y="18" width="13" height="20" rx="6.5" fill="#1677ff" opacity="0.55"/>
            </svg>
          </div>
          <div class="welcome-content">
            <h2 class="welcome-title">你好，我是 SmartCampus 助理 👋</h2>
            <p class="welcome-desc">我可以为你解答学校事务、学院政策、通知公告、学业信息等问题，<br>也能帮你查询选课、考试、成绩等各类信息。</p>
            <div class="welcome-actions">
              <button class="wa-btn" @click="sendQuick('查询本学期校历安排')">
                <el-icon color="#1677ff"><Calendar /></el-icon> 查校历
              </button>
              <button class="wa-btn" @click="sendQuick('本学期什么时候选课')">
                <el-icon color="#17a855"><List /></el-icon> 查选课
              </button>
              <button class="wa-btn" @click="sendQuick('本学期考试时间安排')">
                <el-icon color="#7b5ea7"><Document /></el-icon> 查考试
              </button>
              <button class="wa-btn" @click="sendQuick('查询学校相关政策规定')">
                <el-icon color="#e6832a"><Tickets /></el-icon> 查政策
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div ref="msgListRef" class="message-list" v-show="messages.length > 0">
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="message-row"
          :class="msg.role"
        >
          <!-- 用户消息 -->
          <template v-if="msg.role === 'user'">
            <div class="msg-time-right">{{ msgTime(idx) }}</div>
            <div class="bubble user-bubble">
              <img v-if="msg.imageUrl" :src="msg.imageUrl" class="message-image" alt="上传图片" />
              <div v-if="msg.content">{{ msg.content }}</div>
            </div>
            <el-avatar class="msg-avatar" :size="36" :style="{ background: '#1677ff', fontSize: '13px', fontWeight: 600 }">
              {{ username.charAt(0).toUpperCase() }}
            </el-avatar>
          </template>

          <!-- AI 消息 -->
          <template v-else>
            <div v-if="msg.role === 'teacher'" class="teacher-avatar-wrap">
              <el-avatar :size="36" class="teacher-avatar">师</el-avatar>
            </div>
            <div v-else class="ai-avatar-wrap">
              <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
                <circle cx="18" cy="18" r="18" fill="#e6f0ff"/>
                <rect x="9" y="11" width="18" height="16" rx="6" fill="#1677ff"/>
                <circle cx="13.5" cy="18" r="2.5" fill="white"/>
                <circle cx="22.5" cy="18" r="2.5" fill="white"/>
                <circle cx="14.2" cy="18" r="1" fill="#1677ff"/>
                <circle cx="23.2" cy="18" r="1" fill="#1677ff"/>
                <rect x="12" y="22" width="12" height="2.5" rx="1.25" fill="white" opacity="0.9"/>
                <rect x="5.5" y="14" width="3" height="8" rx="1.5" fill="#1677ff" opacity="0.4"/>
                <rect x="27.5" y="14" width="3" height="8" rx="1.5" fill="#1677ff" opacity="0.4"/>
              </svg>
            </div>
            <div class="bubble" :class="messageBubbleClass(msg.role)">
              <!-- 思考中 -->
              <div v-if="msg.loading" class="thinking-row">
                <span class="thinking-text">正在为你思考中</span>
                <span class="thinking-dots"><span/><span/><span/></span>
              </div>
              <div v-else>
                <div class="msg-text" v-html="renderMarkdown(msg.content)" />
                <!-- 工具调用卡片 -->
                <div v-if="msg.toolResult" class="tool-card">
                  <div class="tool-card-head">
                    <el-icon size="13" color="#0d9488"><Tools /></el-icon>
                    <span class="tool-invoke-label">调用工具查询学业信息</span>
                  </div>
                  <div class="tool-card-body">
                    <div class="tool-fn-row">
                      <el-icon size="13" color="#1677ff"><Operation /></el-icon>
                      <span class="tool-fn-name">{{ toolNameLabel(msg.toolResult.toolName) }}</span>
                      <el-tag size="small" :type="msg.toolResult.success ? 'success' : 'danger'" effect="plain" class="tool-status-tag">
                        {{ msg.toolResult.success ? '查询完成' : '查询失败' }}
                      </el-tag>
                    </div>
                    <div v-if="msg.toolResult.params && Object.keys(msg.toolResult.params).length" class="tool-params-grid">
                      <span v-for="(v, k) in msg.toolResult.params" :key="k" class="tool-param">
                        <span class="tp-key">{{ k }}</span>
                        <span class="tp-val">{{ v }}</span>
                      </span>
                    </div>
                    <div v-if="msg.toolResult.error" class="tool-error">
                      <el-icon size="12"><Warning /></el-icon> {{ msg.toolResult.error }}
                    </div>
                    <div v-else-if="msg.toolResult.data && Array.isArray(msg.toolResult.data) && msg.toolResult.data.length" class="tool-data-table">
                      <table>
                        <thead>
                          <tr><th v-for="col in Object.keys(msg.toolResult.data[0])" :key="col">{{ col }}</th></tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, ri) in msg.toolResult.data" :key="ri">
                            <td v-for="col in Object.keys(msg.toolResult.data[0])" :key="col">{{ row[col] }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                    <div v-if="msg.toolResult.dataSource" class="tool-footer">
                      <el-icon size="11"><Connection /></el-icon>
                      数据来源：{{ msg.toolResult.dataSource }}
                      <span v-if="msg.toolResult.updatedAt">（更新时间：{{ msg.toolResult.updatedAt }}）</span>
                    </div>
                  </div>
                </div>
                <!-- 知识来源 -->
                <div v-if="msg.sourceRefs && msg.sourceRefs.length" class="source-refs">
                  <div class="source-refs-label">知识来源 ({{ msg.sourceRefs.length }})</div>
                  <div v-for="(ref, ri) in msg.sourceRefs" :key="ri" class="source-ref-card">
                    <div class="src-card-left">
                      <el-icon size="14" color="#1677ff"><Document /></el-icon>
                    </div>
                    <div class="src-card-mid">
                      <div class="src-doc-title">《{{ ref.docTitle }}》</div>
                      <div class="src-doc-meta" v-if="ref.headingPath">章节：{{ ref.headingPath }}</div>
                    </div>
                    <div class="src-card-right" v-if="ref.pageStart">
                      第{{ ref.pageStart }}页
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div v-if="selectedImage" class="image-preview-bar">
          <div class="image-preview-card">
            <img :src="selectedImageUrl" alt="待发送图片" />
            <div class="image-preview-meta">
              <span class="image-name">{{ selectedImage.name }}</span>
              <span class="image-size">{{ formatFileSize(selectedImage.size) }}</span>
            </div>
            <button class="image-remove" :disabled="streaming" @click="clearSelectedImage">
              <el-icon :size="12"><Close /></el-icon>
            </button>
          </div>
        </div>
        <div class="input-card">
          <input
            ref="imageInputRef"
            type="file"
            accept="image/*"
            class="image-input"
            @change="handleImageSelected"
          />
          <el-button text circle class="attach-btn" :disabled="streaming" @click="openImagePicker">
            <el-icon :size="18" color="#86909c"><Paperclip /></el-icon>
          </el-button>
          <el-input
            ref="inputRef"
            v-model="inputText"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            placeholder="输入问题，Enter 发送，Shift + Enter 换行"
            resize="none"
            class="chat-input"
            @keydown.enter.exact.prevent="sendMessage"
            @keydown.enter.shift.exact="() => {}"
            :disabled="streaming"
          />
          <el-button
            type="primary"
            circle
            class="send-btn"
            :disabled="(!inputText.trim() && !selectedImage) || streaming"
            @click="sendMessage"
          >
            <el-icon :size="16"><Promotion /></el-icon>
          </el-button>
        </div>
        <div class="quick-btns">
          <button class="quick-btn" @click="sendQuick('查询本学期校历安排')">
            <el-icon color="#1677ff" :size="13"><Calendar /></el-icon> 查校历
          </button>
          <button class="quick-btn" @click="sendQuick('本学期什么时候选课')">
            <el-icon color="#17a855" :size="13"><List /></el-icon> 查选课
          </button>
          <button class="quick-btn" @click="sendQuick('本学期考试时间安排')">
            <el-icon color="#7b5ea7" :size="13"><Document /></el-icon> 查考试
          </button>
          <button class="quick-btn" @click="sendQuick('查询学校相关政策规定')">
            <el-icon color="#e6832a" :size="13"><Tickets /></el-icon> 查政策
          </button>
          <button class="quick-btn handoff-btn" :disabled="streaming" @click="requestHandoff">
            <el-icon color="#fa8c16" :size="13"><Headset /></el-icon> 转人工
          </button>
        </div>
      </div>
    </div>

    <!-- ── 右侧面板 ── -->
    <div class="right-panel">
      <!-- 猜你想问 -->
      <div class="rp-section">
        <div class="rp-header">
          <span class="rp-title">猜你想问</span>
          <button class="rp-refresh" @click="refreshSuggestions">
            <el-icon :size="12"><Refresh /></el-icon> 换一批
          </button>
        </div>
        <div
          v-for="q in displaySuggestions"
          :key="q"
          class="suggest-item"
          @click="sendQuick(q)"
        >
          <el-icon :size="13" color="#1677ff"><Clock /></el-icon>
          <span>{{ q }}</span>
        </div>
      </div>

      <!-- 最近会话 -->
      <div class="rp-section">
        <div class="rp-header">
          <span class="rp-title">最近会话</span>
          <span class="rp-more" @click="newSession">新建</span>
        </div>
        <div
          v-for="s in sessions.slice(0, 6)"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === currentSessionId }"
          @click="switchSession(s)"
        >
          <div class="si-text">{{ s.title || '新对话' }}</div>
          <div class="si-time">{{ formatTime(s.updatedAt) }}</div>
          <div class="si-actions">
            <button class="si-action" title="导出会话" @click.stop="exportSession(s)">
              <el-icon :size="11"><Download /></el-icon>
            </button>
            <button class="si-action danger" title="删除会话" @click.stop="deleteSession(s.sessionId)">
              <el-icon :size="11"><Close /></el-icon>
            </button>
          </div>
        </div>
        <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="48" />
      </div>

      <!-- 服务状态 -->
      <div class="rp-section">
        <div class="rp-title" style="margin-bottom:10px">服务状态</div>
        <div class="status-row">
          <span class="status-dot ok" />
          <span class="status-label">系统运行正常</span>
        </div>
        <div class="status-hint">知识库更新于 2025-05-19 23:30</div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 【导读】学生对话页
 * - sendMessage：EventSource 连接 GET /chat/stream，逐 token 渲染
 * - requestHandoff：POST /chat/handoff 转人工（老师在工作台可见）
 * - currentSessionId：会话 ID，与后端 chat_sessions.session_id 一致
 */
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Promotion, Document, Calendar, List, Warning, Tools, Connection,
  Refresh, Clock, Close, Paperclip, Tickets, Operation, Headset, Download
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'

const authStore = useAuthStore()
const username  = computed(() => authStore.userInfo?.username || 'U')

const sessions         = ref([])
const currentSessionId = ref(null)
const messages         = ref([])
const inputText        = ref('')
const streaming        = ref(false)
const msgListRef       = ref(null)
const inputRef         = ref(null)
const imageInputRef    = ref(null)
const selectedImage    = ref(null)
const selectedImageUrl = ref('')
let messagePollTimer = null

const allSuggestions = [
  '期末考试时间安排是怎样的？',
  '四六级报名时间是什么时候？',
  '如何申请成绩复核？',
  '校园卡充值及使用说明',
  '研究生奖学金评定标准是什么？',
  '毕业论文答辩时间安排',
  '奖学金评定标准',
  '学籍异动如何申请',
  '图书馆借阅规则',
  '校医院就诊流程',
]
const displaySuggestions = ref(allSuggestions.slice(0, 5))

function refreshSuggestions() {
  const start = Math.floor(Math.random() * (allSuggestions.length - 5))
  displaySuggestions.value = allSuggestions.slice(start, start + 5)
}

onMounted(() => {
  loadSessions()
  messagePollTimer = window.setInterval(refreshCurrentMessages, 5000)
})
onUnmounted(() => {
  if (messagePollTimer) window.clearInterval(messagePollTimer)
  revokeSelectedImageUrl()
})

async function loadSessions() {
  try {
    const res = await http.get('/chat/sessions')
    sessions.value = res.data || []
  } catch (e) {
    console.error('Failed to load sessions', e)
  }
}

async function switchSession(s) {
  currentSessionId.value = s.sessionId
  try {
    const res = await http.get(`/chat/sessions/${s.sessionId}/messages`)
    messages.value = (res.data || []).map(m => ({
      role: m.role,
      content: m.content,
      imageUrl: m.imageUrl,
      imageName: m.imageName,
      intent: m.intent,
      sourceRefs: m.sourceRefs || [],
    }))
    scrollToBottom()
  } catch (e) {
    console.error('Failed to load messages', e)
  }
}

async function refreshCurrentMessages() {
  if (!currentSessionId.value || streaming.value) return
  try {
    const res = await http.get(`/chat/sessions/${currentSessionId.value}/messages`)
    const incoming = res.data || []
    if (incoming.length === messages.value.length) return
    messages.value = incoming.map(m => ({
      role: m.role,
      content: m.content,
      imageUrl: m.imageUrl,
      imageName: m.imageName,
      intent: m.intent,
      sourceRefs: m.sourceRefs || [],
    }))
    scrollToBottom()
  } catch (e) {
    console.error('Failed to refresh messages', e)
  }
}

function newSession() {
  currentSessionId.value = null
  messages.value = []
}

async function deleteSession(sessionId) {
  try {
    await http.delete(`/chat/sessions/${sessionId}`)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    if (currentSessionId.value === sessionId) newSession()
    ElMessage.success('会话已删除')
  } catch (e) {
    console.error('Failed to delete session', e)
  }
}

async function exportSession(session) {
  try {
    const res = await fetch(`/api/v1/chat/sessions/${session.sessionId}/export`, {
      headers: authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {},
    })
    if (!res.ok) throw new Error('导出失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${sanitizeDownloadName(session.title || '聊天记录')}.md`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error('Failed to export session', e)
  }
}

function sanitizeDownloadName(name) {
  return name.replace(/[\\/:*?"<>|]/g, '_').slice(0, 60) || '聊天记录'
}

function sendQuick(text) {
  inputText.value = text
  sendMessage()
}

function openImagePicker() {
  imageInputRef.value?.click()
}

function handleImageSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 8 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 8MB')
    return
  }
  revokeSelectedImageUrl()
  selectedImage.value = file
  selectedImageUrl.value = URL.createObjectURL(file)
}

function revokeSelectedImageUrl() {
  if (selectedImageUrl.value) URL.revokeObjectURL(selectedImageUrl.value)
  selectedImageUrl.value = ''
}

function clearSelectedImage() {
  selectedImage.value = null
  revokeSelectedImageUrl()
}

function formatFileSize(size) {
  if (!size) return ''
  if (size < 1024 * 1024) return `${Math.ceil(size / 1024)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}

async function requestHandoff() {
  if (streaming.value) return
  const summary = inputText.value.trim() || '申请转人工'
  inputText.value = ''
  try {
    const res = await http.post('/chat/handoff', {
      sessionId: currentSessionId.value,
      summary,
      urgency: 'MEDIUM',
    })
    currentSessionId.value = res.data?.sessionId || currentSessionId.value
    ElMessage.success('已转人工，老师会在此会话中回复')
    await loadSessions()
    await refreshCurrentMessages()
  } catch (e) {
    console.error('Failed to request handoff', e)
  }
}

async function sendMessage() {
  const query = inputText.value.trim()
  const imageFile = selectedImage.value
  const imageUrl = selectedImageUrl.value
  if ((!query && !imageFile) || streaming.value) return
  inputText.value = ''
  selectedImage.value = null
  selectedImageUrl.value = ''
  streaming.value = true

  messages.value.push({ role: 'user', content: query || '请分析这张图片', imageUrl })
  messages.value.push({ role: 'assistant', content: '', loading: true, sourceRefs: [], intent: null, toolResult: null })
  const assistantIdx = messages.value.length - 1
  await scrollToBottom()

  if (imageFile) {
    await sendImageMessage(query, imageFile, imageUrl, assistantIdx)
    return
  }

  const token  = authStore.token
  const params = new URLSearchParams({ query })
  if (currentSessionId.value) params.append('sessionId', currentSessionId.value)
  if (token) params.append('token', token)

  const evtSource = new EventSource(`/api/v1/chat/stream?${params.toString()}`)

  evtSource.addEventListener('token', (e) => {
    const data = JSON.parse(e.data)
    const msg = messages.value[assistantIdx]
    if (msg.loading) { msg.loading = false; msg.content = '' }
    msg.content += data.token
    scrollToBottom()
  })

  evtSource.addEventListener('done', async (e) => {
    evtSource.close()
    streaming.value = false
    const data = JSON.parse(e.data)
    const msg = messages.value[assistantIdx]
    msg.intent     = data.intent
    msg.toolResult = data.toolResult || null
    msg.sourceRefs = data.sourceRefs || []
    if (data.sessionId && !currentSessionId.value) currentSessionId.value = data.sessionId
    msg.loading = false
    await loadSessions()
    scrollToBottom()
  })

  evtSource.addEventListener('error', () => {
    evtSource.close()
    streaming.value = false
    const msg = messages.value[assistantIdx]
    if (msg.loading || !msg.content) {
      msg.loading = false
      msg.content = '请求失败，请稍后重试。'
    }
  })

  setTimeout(() => {
    if (streaming.value) {
      evtSource.close()
      streaming.value = false
      const msg = messages.value[assistantIdx]
      if (msg.loading) { msg.loading = false; msg.content = '请求超时，请稍后重试。' }
    }
  }, 120000)
}

async function sendImageMessage(query, imageFile, imageUrl, assistantIdx) {
  const msg = () => messages.value[assistantIdx]
  try {
    const form = new FormData()
    form.append('image', imageFile)
    form.append('query', query || '请分析这张图片')
    if (currentSessionId.value) form.append('sessionId', currentSessionId.value)
    const res = await http.post('/chat/image', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 180000,
    })
    const data = res.data || {}
    if (data.imageUrl) {
      const userMsg = messages.value[messages.value.length - 2]
      if (userMsg?.role === 'user') {
        userMsg.imageUrl = data.imageUrl
        userMsg.imageName = data.imageName
      }
    }
    msg().loading = false
    msg().content = data.answer || '图片已解析，但没有生成回答。'
    msg().intent = data.intent
    msg().sourceRefs = data.sourceRefs || []
    msg().toolResult = data.toolResult || null
    if (data.sessionId && !currentSessionId.value) currentSessionId.value = data.sessionId
    await loadSessions()
    scrollToBottom()
  } catch (e) {
    msg().loading = false
    msg().content = e?.message || '图片问答失败，请稍后重试。'
  } finally {
    streaming.value = false
    if (imageUrl) URL.revokeObjectURL(imageUrl)
  }
}

async function scrollToBottom() {
  await nextTick()
  if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight
}

function msgTime(idx) {
  const msg = messages.value[idx]
  if (!msg || msg.role !== 'user') return ''
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts), now = new Date(), diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
}

function toolNameLabel(name) {
  const map = {
    query_academic_calendar: '校历查询',
    query_course_selection:  '选课时间查询',
    query_department_contact: '部门联系方式',
    create_human_ticket: '转人工工单',
  }
  return map[name] || name
}

function messageBubbleClass(role) {
  return role === 'teacher' ? 'teacher-bubble' : 'assistant-bubble'
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: #f5f7fa;
}

/* ── Chat main ── */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  background: #f5f7fa;
}

/* ── Welcome banner ── */
.welcome-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.welcome-banner {
  width: 100%;
  max-width: 780px;
  background: linear-gradient(135deg, #eaf1ff 0%, #d4e4ff 60%, #e8f5e9 100%);
  border-radius: 20px;
  padding: 28px 36px;
  display: flex;
  align-items: center;
  gap: 32px;
  box-shadow: 0 2px 12px rgba(22,119,255,0.08);
}
.welcome-robot { flex-shrink: 0; }
.welcome-content { flex: 1; }
.welcome-title {
  font-size: 22px;
  font-weight: 700;
  color: #1d2129;
  margin: 0 0 10px;
  line-height: 1.3;
}
.welcome-desc {
  font-size: 14px;
  color: #4e5969;
  line-height: 1.7;
  margin: 0 0 20px;
}
.welcome-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.wa-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: white;
  border: 1px solid #d4e4ff;
  border-radius: 8px;
  font-size: 13px;
  color: #1d2129;
  cursor: pointer;
  font-weight: 500;
  transition: all .15s;
}
.wa-btn:hover {
  border-color: #1677ff;
  background: #f0f5ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(22,119,255,.12);
}

/* ── Message list ── */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background: #f5f7fa;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.message-row.user {
  justify-content: flex-end;
  align-items: flex-start;
}

.msg-avatar { flex-shrink: 0; margin-top: 2px; }
.ai-avatar-wrap { flex-shrink: 0; margin-top: 2px; }
.teacher-avatar-wrap { flex-shrink: 0; margin-top: 2px; }
.teacher-avatar {
  background: #fa8c16;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.msg-time-right {
  font-size: 11px;
  color: #c2c7d0;
  align-self: flex-end;
  margin-bottom: 4px;
  flex-shrink: 0;
}

.bubble {
  max-width: 68%;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.65;
}
.user-bubble {
  padding: 11px 16px;
  background: #1677ff;
  color: white;
  border-radius: 14px 14px 4px 14px;
}
.message-image {
  display: block;
  max-width: 260px;
  max-height: 180px;
  border-radius: 10px;
  object-fit: cover;
  margin-bottom: 8px;
  background: rgba(255,255,255,.18);
}
.message-image:only-child { margin-bottom: 0; }
.assistant-bubble {
  padding: 14px 16px;
  background: white;
  border: 1px solid #e8ecf0;
  border-radius: 4px 14px 14px 14px;
  color: #1d2129;
  min-width: 80px;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
}
.teacher-bubble {
  padding: 14px 16px;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px 14px 14px 14px;
  color: #1d2129;
  min-width: 80px;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
}

/* Thinking animation */
.thinking-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
  color: #86909c;
  font-size: 13px;
}
.thinking-dots {
  display: flex;
  gap: 3px;
  align-items: center;
}
.thinking-dots span {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #1677ff;
  animation: bounce 1.2s infinite;
}
.thinking-dots span:nth-child(2) { animation-delay: .2s; }
.thinking-dots span:nth-child(3) { animation-delay: .4s; }
@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-5px); }
}

.msg-text :deep(p) { margin: 0 0 8px; }
.msg-text :deep(p:last-child) { margin-bottom: 0; }
.msg-text :deep(code) { background: #f5f7fa; padding: 1px 5px; border-radius: 3px; font-size: 12px; }
.msg-text :deep(strong) { color: #1d2129; }

/* Tool card */
.tool-card {
  margin-top: 12px;
  border: 1px solid #d9f7f0;
  border-radius: 10px;
  overflow: hidden;
  font-size: 12px;
}
.tool-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  background: #e6fcf5;
  border-bottom: 1px solid #d9f7f0;
  color: #0d9488;
  font-size: 12px;
  font-weight: 500;
}
.tool-invoke-label { flex: 1; }
.tool-card-body { padding: 10px 12px; background: #f9fffe; }
.tool-fn-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.tool-fn-name { font-weight: 600; color: #1677ff; font-size: 12px; flex: 1; }
.tool-status-tag { flex-shrink: 0; }
.tool-params-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.tool-param {
  background: white;
  border: 1px solid #d9f7f0;
  border-radius: 6px;
  padding: 3px 8px;
  display: flex;
  gap: 4px;
  font-size: 11px;
}
.tp-key { color: #0d9488; font-weight: 500; }
.tp-val { color: #374151; }
.tool-error { color: #dc2626; display: flex; align-items: center; gap: 4px; }
.tool-data-table { overflow-x: auto; margin-bottom: 8px; }
.tool-data-table table { width: 100%; border-collapse: collapse; }
.tool-data-table th { background: #e6fcf5; padding: 4px 10px; text-align: left; font-weight: 600; color: #0d9488; border: 1px solid #d9f7f0; white-space: nowrap; }
.tool-data-table td { padding: 4px 10px; border: 1px solid #d9f7f0; color: #374151; }
.tool-footer {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #86909c;
  font-size: 11px;
  border-top: 1px solid #d9f7f0;
  margin-top: 8px;
  padding-top: 6px;
}

/* Source refs */
.source-refs { margin-top: 12px; border-top: 1px solid #f0f2f5; padding-top: 10px; }
.source-refs-label { font-size: 11px; color: #86909c; margin-bottom: 6px; }
.source-ref-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f0f5ff;
  border: 1px solid #d4e4ff;
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 12px;
}
.src-card-left { flex-shrink: 0; }
.src-card-mid { flex: 1; min-width: 0; }
.src-doc-title { color: #1d2129; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.src-doc-meta { color: #86909c; font-size: 11px; margin-top: 1px; }
.src-card-right { flex-shrink: 0; color: #86909c; font-size: 11px; }

/* ── Input area ── */
.input-area {
  padding: 12px 20px 14px;
  background: #f5f7fa;
  border-top: 1px solid #e8ecf0;
}
.image-preview-bar {
  display: flex;
  margin-bottom: 8px;
}
.image-preview-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 360px;
  padding: 7px 34px 7px 8px;
  background: #fff;
  border: 1px solid #d4e4ff;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(22,119,255,.08);
}
.image-preview-card img {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f7fa;
}
.image-preview-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.image-name {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 500;
  color: #1d2129;
}
.image-size {
  font-size: 11px;
  color: #86909c;
}
.image-remove {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  color: #86909c;
  background: #f5f7fa;
  cursor: pointer;
}
.image-remove:hover {
  color: #ef4444;
  background: #fff1f0;
}
.input-card {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: white;
  border: 1px solid #e8ecf0;
  border-radius: 12px;
  padding: 8px 10px 8px 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,.04);
  transition: border-color .15s;
}
.input-card:focus-within { border-color: #1677ff; box-shadow: 0 0 0 2px rgba(22,119,255,.08); }
.image-input { display: none; }
.attach-btn { color: #c2c7d0; flex-shrink: 0; }
.attach-btn:hover { color: #1677ff !important; }
.chat-input { flex: 1; }
.chat-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 14px;
  background: transparent;
  line-height: 1.6;
  resize: none;
}
.chat-input :deep(.el-textarea__inner:focus) { box-shadow: none; }
.send-btn {
  flex-shrink: 0;
  width: 36px !important;
  height: 36px !important;
}

.quick-btns {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.quick-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  background: white;
  border: 1px solid #e8ecf0;
  border-radius: 20px;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  transition: all .15s;
}
.quick-btn:hover { border-color: #1677ff; color: #1677ff; background: #f0f5ff; }
.handoff-btn:hover { border-color: #fa8c16; color: #fa8c16; background: #fff7e6; }

/* Scrollbars */
.message-list::-webkit-scrollbar { width: 4px; }
.message-list::-webkit-scrollbar-thumb { background: #e0e3e8; border-radius: 2px; }

/* ── Right panel ── */
.right-panel {
  width: 248px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e8ecf0;
  overflow-y: auto;
  padding: 16px 0;
}
.right-panel::-webkit-scrollbar { width: 3px; }
.right-panel::-webkit-scrollbar-thumb { background: #e0e3e8; border-radius: 2px; }

.rp-section {
  padding: 0 14px 16px;
  border-bottom: 1px solid #f5f7fa;
  margin-bottom: 4px;
}
.rp-section:last-child { border-bottom: none; }

.rp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.rp-title { font-size: 13px; font-weight: 600; color: #1d2129; }
.rp-refresh {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #1677ff;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}
.rp-more {
  font-size: 11px;
  color: #1677ff;
  cursor: pointer;
}
.rp-more:hover { text-decoration: underline; }

.suggest-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 6px 0;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  line-height: 1.4;
  border-bottom: 1px dashed #f5f7fa;
  transition: color .15s;
}
.suggest-item:hover { color: #1677ff; }
.suggest-item:last-child { border-bottom: none; }

.session-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 6px;
  border-radius: 8px;
  cursor: pointer;
  transition: background .15s;
  margin-bottom: 2px;
}
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #f0f5ff; }
.session-item.active .si-text { color: #1677ff; font-weight: 500; }
.si-text {
  flex: 1;
  font-size: 12px;
  color: #4e5969;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.si-time { font-size: 11px; color: #c2c7d0; flex-shrink: 0; }
.si-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: .72;
}
.session-item:hover .si-actions,
.session-item.active .si-actions {
  opacity: 1;
}
.si-action {
  flex-shrink: 0;
  background: none;
  border: none;
  width: 20px;
  height: 20px;
  padding: 0;
  cursor: pointer;
  color: #c2c7d0;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.si-action:hover { color: #1677ff; background: #fff; }
.si-action.danger:hover { color: #ef4444; background: #fff; }

.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #1d2129;
  margin-bottom: 4px;
}
.status-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.ok { background: #17a855; }
.status-label { font-weight: 500; }
.status-hint { font-size: 11px; color: #86909c; padding-left: 16px; }
</style>
