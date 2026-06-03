<template>
  <div class="settings-page">
    <!-- ── 页面头部 ── -->
    <div class="page-header">
      <div>
        <h2 class="page-title">系统设置</h2>
        <p class="page-subtitle">配置智能助手的模型、提示词、工具与知识体系，打造安全、可靠、可控的校园服务体验。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" size="small" @click="resetDefaults">恢复默认配置</el-button>
        <el-button type="primary" :icon="Check" @click="saveAll" :loading="saving">保存并应用</el-button>
        <input ref="importInput" type="file" accept=".json" hidden @change="handleImport" />
      </div>
    </div>

    <!-- ── 主体：左侧 tabs + 右侧 sidebar ── -->
    <div class="settings-body">
      <!-- 左侧主内容 -->
      <div class="settings-main">
        <el-tabs v-model="activeTab" class="settings-tabs">

          <!-- Tab: 基础配置 -->
          <el-tab-pane label="基础配置" name="base">
            <div class="tab-base-grid">

              <!-- 模型配置 (top-left) -->
              <div class="section-card">
                <div class="section-head">
                  <el-icon color="#1677ff"><Setting /></el-icon>
                  <span class="section-title">模型配置</span>
                  <span class="section-sub">配置各能力所使用的模型及生成参数</span>
                </div>
                <el-table :data="modelConfigs" border class="model-table" size="small">
                  <el-table-column label="能力角色" width="160">
                    <template #default="{ row }">
                      <div class="role-cell">
                        <div class="role-icon-badge" :style="{ background: row.bgColor }">
                          <el-icon :size="13" :color="row.iconColor"><component :is="row.iconName" /></el-icon>
                        </div>
                        <div>
                          <div class="role-name">{{ row.label }}</div>
                          <div class="role-desc">{{ row.desc }}</div>
                        </div>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="模型选择" width="150">
                    <template #default="{ row }">
                      <el-select v-model="form[row.modelKey]" size="small" style="width:100%">
                        <el-option v-for="opt in modelOptions" :key="opt" :label="opt" :value="opt" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="温度 (Temperature)" width="160">
                    <template #default="{ row }">
                      <div v-if="row.tempKey" class="slider-cell">
                        <el-slider v-model="formNum[row.tempKey]" :min="0" :max="1" :step="0.1" size="small" style="flex:1" />
                        <span class="slider-val">{{ formNum[row.tempKey] }}</span>
                      </div>
                      <span v-else class="text-muted">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="最大 Token">
                    <template #default="{ row }">
                      <el-input-number
                        v-if="row.tokensKey"
                        v-model="formNum[row.tokensKey]"
                        :min="256" :max="8192" :step="256"
                        size="small" style="width:100%"
                      />
                      <span v-else class="text-muted">—</span>
                    </template>
                  </el-table-column>
                </el-table>
                <div class="section-note">
                  <el-icon :size="12"><InfoFilled /></el-icon>
                  温度越低输出越稳定，越高越有创造性；Token 过大会增加延迟与成本。
                  <span class="note-link" @click="activeTab = 'models'">高级参数设置 ›</span>
                </div>
              </div>

              <!-- Prompt 模板 (top-right) -->
              <div class="section-card prompt-section">
                <div class="section-head">
                  <el-icon color="#fa8c16"><Document /></el-icon>
                  <span class="section-title">Prompt 模板</span>
                  <span class="section-sub">管理系统各场景的提示词模板</span>
                  <div style="flex:1" />
                  <el-select v-model="promptScene" size="small" style="width:160px" placeholder="选择场景">
                    <el-option
                      v-for="scene in promptSceneOptions"
                      :key="scene.value"
                      :label="scene.label"
                      :value="scene.value"
                    />
                  </el-select>
                </div>
                <div class="prompt-editor-wrap">
                  <div class="prompt-line-nums">
                    <span v-for="n in 14" :key="n">{{ n }}</span>
                  </div>
                  <el-input
                    v-model="form[selectedPromptKey]"
                    type="textarea"
                    :rows="14"
                    resize="none"
                    class="prompt-editor"
                    :placeholder="selectedPromptTemplate.placeholder"
                  />
                </div>
                <div class="prompt-footer">
                  <el-button size="small" plain @click="copySelectedPrompt">
                    <el-icon><CopyDocument /></el-icon> 复制模板
                  </el-button>
                </div>
              </div>

              <!-- 工具开关 (bottom-left) -->
              <div class="section-card">
                <div class="section-head">
                  <el-icon color="#17a855"><Tools /></el-icon>
                  <span class="section-title">工具开关</span>
                  <span class="section-sub">控制可调用工具的启用状态与可见范围</span>
                </div>
                <div v-for="t in toolList" :key="t.enabledKey" class="tool-toggle-row">
                  <div class="tt-left">
                    <div class="tool-icon-badge" :style="{ background: t.bgColor }">
                      <el-icon :size="14" :color="t.color"><component :is="t.icon" /></el-icon>
                    </div>
                    <div class="tt-info">
                      <div class="tt-name">{{ t.name }}</div>
                      <div class="tt-desc">{{ t.desc }}</div>
                    </div>
                  </div>
                  <div class="tt-right">
                    <el-switch v-model="formBool[t.enabledKey]" size="small" />
                    <el-button size="small" link type="primary" @click="activeTab = 'tools'">配置</el-button>
                  </div>
                </div>
              </div>

            </div>
            <div class="tab-bottom-note">
              <el-icon :size="12"><InfoFilled /></el-icon>
              配置变更将在保存并应用后生效，部分变更可能需要刷新合话或重建索引。
            </div>
          </el-tab-pane>

          <!-- Tab: 模型设置 -->
          <el-tab-pane label="模型设置" name="models">
            <div class="tab-content">
              <div v-for="m in modelConfigs" :key="m.key" class="model-card">
                <div class="model-card-header">
                  <el-icon :size="18" :color="m.iconColor"><component :is="m.iconName" /></el-icon>
                  <el-tag :type="m.tagType" effect="plain" size="small">{{ m.role }}</el-tag>
                  <span class="model-card-title">{{ m.label }}</span>
                </div>
                <el-form label-width="110px" size="small">
                  <el-form-item label="模型">
                    <el-select v-model="form[m.modelKey]" style="width:220px">
                      <el-option v-for="opt in modelOptions" :key="opt" :label="opt" :value="opt" />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="m.tempKey" label="Temperature">
                    <div class="slider-row">
                      <el-slider v-model="formNum[m.tempKey]" :min="0" :max="1" :step="0.1" style="width:180px" show-stops />
                      <span class="slider-val">{{ formNum[m.tempKey] }}</span>
                    </div>
                  </el-form-item>
                  <el-form-item v-if="m.tokensKey" label="Max Tokens">
                    <el-input-number v-model="formNum[m.tokensKey]" :min="256" :max="8192" :step="256" />
                  </el-form-item>
                </el-form>
                <div class="model-card-desc">{{ m.fullDesc }}</div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab: Prompt 模板 -->
          <el-tab-pane label="Prompt模板" name="prompts">
            <div class="tab-content">
              <div class="prompt-grid">
                <div v-for="p in promptTemplates" :key="p.key" class="prompt-config-card">
                  <div class="prompt-config-head">
                    <div>
                      <div class="prompt-config-title">{{ p.label }}</div>
                      <div class="prompt-hint">{{ p.hint }}</div>
                    </div>
                    <div class="prompt-actions">
                      <el-button size="small" plain @click="copyPrompt(p.key)">
                        <el-icon><CopyDocument /></el-icon> 复制
                      </el-button>
                      <el-button size="small" @click="resetPrompt(p.key)">恢复默认</el-button>
                    </div>
                  </div>
                  <el-input
                    v-model="form[p.key]"
                    type="textarea"
                    :rows="9"
                    :placeholder="p.placeholder"
                    resize="none"
                    class="prompt-editor-full"
                  />
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab: 工具配置 -->
          <el-tab-pane label="工具配置" name="tools">
            <div class="tab-content">
              <div class="tool-config-layout">
                <div class="tool-config-list">
                  <div v-for="tool in toolList" :key="tool.enabledKey" class="tool-config-card">
                    <div class="tt-left">
                      <div class="tool-icon-badge" :style="{ background: tool.bgColor }">
                        <el-icon :size="14" :color="tool.color"><component :is="tool.icon" /></el-icon>
                      </div>
                      <div class="tt-info">
                        <div class="tt-name">{{ tool.name }}</div>
                        <div class="tt-desc">{{ tool.desc }}</div>
                        <div class="tool-key">{{ tool.enabledKey }}</div>
                      </div>
                    </div>
                    <el-switch v-model="formBool[tool.enabledKey]" active-text="启用" inactive-text="关闭" />
                  </div>
                </div>
                <div class="tool-model-panel">
                  <div class="tab-section-head">
                    <span>工具调用模型</span>
                    <el-tag size="small" type="success" effect="light">Function Calling</el-tag>
                  </div>
                  <el-form label-width="105px" size="default">
                    <el-form-item label="模型">
                      <el-select v-model="form['models.tool-caller.model']" style="width:220px">
                        <el-option v-for="opt in modelOptions" :key="opt" :label="opt" :value="opt" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="Temperature">
                      <div class="slider-row">
                        <el-slider v-model="formNum['models.tool-caller.temperature']" :min="0" :max="1" :step="0.1" style="width:200px" show-stops />
                        <span class="slider-val">{{ formNum['models.tool-caller.temperature'] }}</span>
                      </div>
                    </el-form-item>
                    <el-form-item label="Max Tokens">
                      <el-input-number v-model="formNum['models.tool-caller.max-tokens']" :min="256" :max="4096" :step="256" />
                    </el-form-item>
                  </el-form>
                  <div class="section-note">
                    <el-icon :size="12"><InfoFilled /></el-icon>
                    关闭工具后，模型不会收到该工具定义；所有工具关闭时会降级为文字提示。
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab: RAG 参数 -->
          <el-tab-pane label="安全策略" name="rag">
            <div class="tab-content">
              <div class="safety-grid">
                <div class="safety-card">
                  <div class="tab-section-head"><span>召回范围</span></div>
                  <el-form label-width="150px" size="default">
                    <el-form-item label="主问题 Child TopK">
                      <el-input-number v-model="formNum['rag.topk.child']" :min="5" :max="50" />
                      <span class="param-hint">主问题向量与 BM25 召回量</span>
                    </el-form-item>
                    <el-form-item label="子问题 Child TopK">
                      <el-input-number v-model="formNum['rag.topk.child_sub']" :min="1" :max="30" />
                      <span class="param-hint">查询改写后的子问题召回量</span>
                    </el-form-item>
                    <el-form-item label="Parent 回捞数">
                      <el-input-number v-model="formNum['rag.topk.parent']" :min="1" :max="10" />
                      <span class="param-hint">送入 LLM 的 Parent 段落数</span>
                    </el-form-item>
                  </el-form>
                </div>
                <div class="safety-card">
                  <div class="tab-section-head"><span>重排与命中</span></div>
                  <el-form label-width="150px" size="default">
                    <el-form-item label="Rerank 保留数">
                      <el-input-number v-model="formNum['rag.rerank.top_n']" :min="1" :max="30" />
                      <span class="param-hint">重排后保留的候选数量</span>
                    </el-form-item>
                    <el-form-item label="Rerank 分数阈值">
                      <div class="slider-row">
                        <el-slider v-model="formNum['rag.rerank.score_thresh']" :min="0" :max="1" :step="0.05" style="width:220px" show-stops />
                        <span class="slider-val">{{ formNum['rag.rerank.score_thresh'] }}</span>
                      </div>
                    </el-form-item>
                    <el-form-item label="FAQ 精确命中">
                      <div class="slider-row">
                        <el-slider v-model="formNum['faq.match.exact_thresh']" :min="0.5" :max="1" :step="0.01" style="width:220px" show-stops />
                        <span class="slider-val">{{ formNum['faq.match.exact_thresh'] }}</span>
                      </div>
                    </el-form-item>
                    <el-form-item label="FAQ 候选命中">
                      <div class="slider-row">
                        <el-slider v-model="formNum['faq.match.candidate_thresh']" :min="0.5" :max="1" :step="0.01" style="width:220px" show-stops />
                        <span class="slider-val">{{ formNum['faq.match.candidate_thresh'] }}</span>
                      </div>
                    </el-form-item>
                  </el-form>
                </div>
              </div>
            </div>
          </el-tab-pane>

        </el-tabs>
      </div>

      <!-- 右侧 sidebar -->
      <div class="settings-sidebar">
        <!-- 配置状态 -->
        <div class="sb-section">
          <div class="sb-title">配置状态</div>
          <div class="config-status-card">
            <el-icon :size="18" color="#17a855"><CircleCheck /></el-icon>
            <div class="cs-info">
              <div class="cs-label" style="color:#17a855;font-weight:600">配置已同步</div>
              <div class="cs-sub">所有配置已成功同步到当前环境</div>
            </div>
          </div>
          <div class="sb-kv"><span class="sbk">最近更新</span><span class="sbv">{{ lastUpdatedAt || '暂无记录' }}</span></div>
          <div class="sb-kv"><span class="sbk">更新人</span><span class="sbv">{{ nickname || '当前用户' }}</span></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check, Download, Upload, Refresh, Setting, Tools, Document,
  InfoFilled, CopyDocument, CircleCheck, Clock
} from '@element-plus/icons-vue'
import { settingsApi } from '../api/settings'
import { useAuthStore } from '../stores/auth'

const auth     = useAuthStore()
const nickname = computed(() => auth.nickname)

const activeTab   = ref('base')
const saving      = ref(false)
const importInput = ref(null)
const promptScene = ref('rag')
const lastUpdatedAt = ref('')

const form     = reactive({})
const formNum  = reactive({})
const formBool = reactive({})

const modelOptions = ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen-long', 'qwen-max-latest']

const defaultPrompt = `你是 SmartCampus 智能助理，一名专业、友善的校园服务助手。
你的目标是基于学校官方知识与工具，准确、简洁地回答学生的问题。
请遵循以下原则：
- 优先使用知识库中的内容进行回答；
- 若信息不足，主动说明并提供可行的建议或进一步指引；
- 回答使用中文，语气亲切、专业；
- 严禁编造信息，涉及敏感或隐私内容时通过安全策略。

可用上下文：
{context}

学生问题：{question}

请给出结构化、清晰的回答：`

const promptDefaults = {
  'prompt.rag_default': defaultPrompt,
  'prompt.academic_default': '你是SmartCampus校园智能助手，擅长查询教务信息。请根据用户需求选择合适的工具获取准确信息，并以友好清晰的方式回答。',
  'prompt.chitchat_default': '你是SmartCampus校园智能助手，友好专业。日常闲聊轻松回应，校园问题引导使用功能查询。'
}

const modelConfigs = [
  {
    key: 'intent', role: '意图路由', label: 'Intent Router', tagType: '', iconName: 'Operation', iconColor: '#1677ff', bgColor: '#e6f0ff',
    modelKey: 'models.intent-router.model', tempKey: 'models.intent-router.temperature', tokensKey: 'models.intent-router.max-tokens',
    desc: '意图识别与路由', fullDesc: '快速分类用户意图（5类），推荐 qwen-turbo 降低延迟。',
  },
  {
    key: 'rag', role: 'RAG 生成', label: 'RAG Generator', tagType: 'primary', iconName: 'MagicStick', iconColor: '#7b5ea7', bgColor: '#f5f0ff',
    modelKey: 'models.rag-generator.model', tempKey: 'models.rag-generator.temperature', tokensKey: 'models.rag-generator.max-tokens',
    desc: '检索增强生成', fullDesc: '用于根据知识库上下文生成最终回答，推荐 qwen-max 获得最佳质量。',
  },
  {
    key: 'tool', role: '工具调用', label: 'Tool Caller', tagType: 'success', iconName: 'Setting', iconColor: '#17a855', bgColor: '#f0fdf4',
    modelKey: 'models.tool-caller.model', tempKey: null, tokensKey: null,
    desc: '工具调用与编排', fullDesc: '用于教务类结构化查询的 Function Calling，推荐 qwen-plus。',
  },
  {
    key: 'chitchat', role: '闲聊', label: 'Chitchat', tagType: 'info', iconName: 'ChatDotRound', iconColor: '#86909c', bgColor: '#f5f7fa',
    modelKey: 'models.chitchat.model', tempKey: 'models.chitchat.temperature', tokensKey: 'models.chitchat.max-tokens',
    desc: '闲聊与通用问答', fullDesc: '处理日常闲聊，推荐 qwen-turbo 节省成本。',
  },
]

const promptTemplates = [
  { key: 'prompt.rag_default',      label: 'RAG 默认 Prompt',  hint: '可用占位符：{context} {question} {history}', placeholder: '请根据参考资料回答问题...' },
  { key: 'prompt.academic_default', label: '教务工具 Prompt',  hint: '工具调用场景使用',  placeholder: '你是教务信息助手...' },
  { key: 'prompt.chitchat_default', label: '闲聊 Prompt',      hint: '日常对话场景使用', placeholder: '你是SmartCampus助手...' },
]

const promptSceneOptions = [
  { value: 'rag', label: '学业咨询（默认）', key: 'prompt.rag_default' },
  { value: 'academic', label: '教务工具', key: 'prompt.academic_default' },
  { value: 'chitchat', label: '日常闲聊', key: 'prompt.chitchat_default' },
]

const selectedPromptTemplate = computed(() => {
  const scene = promptSceneOptions.find(item => item.value === promptScene.value) || promptSceneOptions[0]
  return promptTemplates.find(item => item.key === scene.key) || promptTemplates[0]
})
const selectedPromptKey = computed(() => selectedPromptTemplate.value.key)

const toolList = [
  { name: '校历查询',   icon: 'Calendar', color: '#1677ff', bgColor: '#e6f0ff', desc: '查询学期周次、节假日与校历安排',     enabledKey: 'tool.query_academic_calendar.enabled' },
  { name: '选课安排',   icon: 'List',     color: '#17a855', bgColor: '#f0fdf4', desc: '查询选课时间、规则与课程信息',       enabledKey: 'tool.query_course_selection.enabled' },
  { name: '部门联系方式', icon: 'Iphone', color: '#fa8c16', bgColor: '#fff7ed', desc: '查询院系部门电话、办公地点与邮箱',   enabledKey: 'tool.query_department_contact.enabled' },
  { name: '工单创建',   icon: 'Tickets',  color: '#ff4d4f', bgColor: '#fff1f0', desc: '提交问题工单并跟踪处理进度',         enabledKey: 'tool.create_human_ticket.enabled' },
]

onMounted(async () => {
  await loadSettings()
})

async function loadSettings() {
  try {
    clearReactive(form)
    clearReactive(formNum)
    clearReactive(formBool)
    const res = await settingsApi.list()
    const configs = res.data || {}
    let latest = ''
    for (const [key, meta] of Object.entries(configs)) {
      const val  = meta.value ?? ''
      const type = meta.type ?? 'STRING'
      if (type === 'BOOLEAN') formBool[key] = val === 'true' || val === '1'
      else if (type === 'NUMBER') formNum[key] = parseFloat(val) || 0
      else form[key] = val
      if (meta.updatedAt && meta.updatedAt > latest) latest = meta.updatedAt
    }
    lastUpdatedAt.value = formatDateTime(latest)
    applyConfigDefaults()
  } catch {
    ElMessage.error('加载配置失败')
  }
}

function clearReactive(target) {
  Object.keys(target).forEach(key => delete target[key])
}

function applyConfigDefaults() {
    // Defaults
    if (formNum['models.rag-generator.temperature'] === undefined) formNum['models.rag-generator.temperature'] = 0.2
    if (formNum['models.rag-generator.max-tokens'] === undefined)  formNum['models.rag-generator.max-tokens'] = 2048
    if (formNum['models.intent-router.temperature'] === undefined) formNum['models.intent-router.temperature'] = 0.1
    if (formNum['models.intent-router.max-tokens'] === undefined)  formNum['models.intent-router.max-tokens'] = 16
    if (formNum['models.query-rewriter.temperature'] === undefined) formNum['models.query-rewriter.temperature'] = 0.2
    if (formNum['models.query-rewriter.max-tokens'] === undefined)  formNum['models.query-rewriter.max-tokens'] = 512
    if (formNum['models.tool-caller.temperature'] === undefined)   formNum['models.tool-caller.temperature'] = 0.1
    if (formNum['models.tool-caller.max-tokens'] === undefined)    formNum['models.tool-caller.max-tokens'] = 1024
    if (formNum['models.chitchat.temperature'] === undefined)      formNum['models.chitchat.temperature'] = 0.7
    if (formNum['models.chitchat.max-tokens'] === undefined)       formNum['models.chitchat.max-tokens'] = 1024
    if (formNum['rag.topk.child']          === undefined) formNum['rag.topk.child']          = 20
    if (formNum['rag.topk.child_sub']      === undefined) formNum['rag.topk.child_sub']      = 7
    if (formNum['rag.topk.parent']         === undefined) formNum['rag.topk.parent']         = 5
    if (formNum['rag.rerank.score_thresh'] === undefined) formNum['rag.rerank.score_thresh'] = 0.3
    if (formNum['rag.rerank.top_n']        === undefined) formNum['rag.rerank.top_n']        = 12
    if (formNum['faq.match.exact_thresh']  === undefined) formNum['faq.match.exact_thresh']  = 0.92
    if (formNum['faq.match.candidate_thresh'] === undefined) formNum['faq.match.candidate_thresh'] = 0.85
    for (const t of toolList) {
      if (formBool[t.enabledKey] === undefined) formBool[t.enabledKey] = true
    }
    if (!form['models.intent-router.model'])    form['models.intent-router.model']   = 'qwen-turbo'
    if (!form['models.rag-generator.model'])    form['models.rag-generator.model']   = 'qwen-max'
    if (!form['models.tool-caller.model'])      form['models.tool-caller.model']     = 'qwen-plus'
    if (!form['models.chitchat.model'])         form['models.chitchat.model']        = 'qwen-turbo'
    for (const [key, value] of Object.entries(promptDefaults)) {
      if (!form[key]) form[key] = value
    }
}

function formatDateTime(value) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 19)
}

async function saveAll() {
  saving.value = true
  try {
    const payload = { ...form }
    for (const [k, v] of Object.entries(formNum))  payload[k] = String(v)
    for (const [k, v] of Object.entries(formBool)) payload[k] = v ? 'true' : 'false'
    await settingsApi.update(payload)
    ElMessage.success('配置已保存并应用')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

function resetDefaults() {
  ElMessageBox.confirm('确认恢复为默认配置？此操作不可撤销。', '提示', { type: 'warning' })
    .then(async () => {
      await settingsApi.reset()
      await loadSettings()
      ElMessage.success('已恢复默认配置')
    })
    .catch(() => {})
}

async function copySelectedPrompt() {
  await copyPrompt(selectedPromptKey.value)
}

async function copyPrompt(key) {
  try {
    await navigator.clipboard.writeText(form[key] || '')
    ElMessage.success('模板已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function resetPrompt(key) {
  form[key] = promptDefaults[key] || ''
  ElMessage.success('已恢复当前模板默认值')
}

async function handleExport() {
  try {
    const res  = await settingsApi.export()
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href = url; a.download = `campus-config-${new Date().toISOString().slice(0,10)}.json`; a.click()
    URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

function triggerImport() { importInput.value.click() }

async function handleImport(e) {
  const file = e.target.files[0]
  if (!file) return
  try {
    const text = await file.text()
    const data = JSON.parse(text)
    await ElMessageBox.confirm('确认导入配置？将覆盖当前设置。', '导入确认', { type: 'warning' })
    await settingsApi.import(data)
    ElMessage.success('导入成功，正在重新加载...')
    setTimeout(() => location.reload(), 1000)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('导入失败：' + (e.message || '格式错误'))
  } finally { e.target.value = '' }
}
</script>

<style scoped>
.settings-page {
  height: 100%;
  overflow-y: auto;
  background: #f5f7fa;
  padding: 20px 24px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;
}
.page-title    { margin: 0 0 4px; font-size: 20px; font-weight: 700; color: #1d2129; }
.page-subtitle { margin: 0; font-size: 13px; color: #86909c; line-height: 1.5; }
.header-actions { display: flex; gap: 10px; align-items: center; flex-shrink: 0; }

/* ── Body layout ── */
.settings-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.settings-main {
  flex: 1;
  min-width: 0;
  background: white;
  border-radius: 12px;
  border: 1px solid #f0f2f5;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.settings-tabs :deep(.el-tabs__header) { padding: 0 16px; border-bottom: 1px solid #f0f2f5; margin: 0; }
.settings-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }

.tab-content { padding: 20px 24px; }

/* ── 基础配置 tab ── */
.tab-base-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
  padding: 16px 16px 0;
}

.section-card {
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 14px 16px;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}
.section-title { font-size: 14px; font-weight: 600; color: #1d2129; }
.section-sub   { font-size: 12px; color: #86909c; margin-left: 2px; }

.model-table :deep(.el-table__header-wrapper th) {
  background: #f5f7fa;
  color: #4e5969;
  font-size: 12px;
  padding: 8px 0;
}
.role-cell { display: flex; align-items: center; gap: 8px; }
.role-name { font-size: 12px; font-weight: 500; color: #1d2129; }
.role-desc { font-size: 11px; color: #86909c; }
.role-icon-badge {
  width: 26px; height: 26px; border-radius: 6px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.tool-icon-badge {
  width: 30px; height: 30px; border-radius: 7px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.slider-cell { display: flex; align-items: center; gap: 8px; }
.slider-val { font-size: 12px; color: #1677ff; font-weight: 600; min-width: 28px; }
.text-muted { color: #c2c7d0; font-size: 12px; }
.section-note {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #86909c;
  margin-top: 10px;
}
.note-link { color: #1677ff; cursor: pointer; }
.note-link:hover { text-decoration: underline; }

.tool-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f2f5;
}
.tool-toggle-row:last-child { border-bottom: none; }
.tt-left { display: flex; align-items: center; gap: 10px; }
.tt-info {}
.tt-name { font-size: 13px; color: #1d2129; font-weight: 500; }
.tt-desc { font-size: 11px; color: #86909c; }
.tt-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

.prompt-section { display: flex; flex-direction: column; height: 100%; }
.prompt-editor-wrap {
  display: flex;
  flex: 1;
  border: 1px solid #313244;
  border-radius: 8px;
  overflow: hidden;
  margin-top: 10px;
}
.prompt-line-nums {
  display: flex;
  flex-direction: column;
  background: #1e1e2e;
  padding: 8px 6px 8px 10px;
  min-width: 30px;
  text-align: right;
  user-select: none;
}
.prompt-line-nums span { font-size: 12px; color: #4e5269; line-height: 1.6; font-family: 'Courier New', monospace; }
.prompt-editor :deep(.el-textarea__inner) {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  background: #1e1e2e;
  color: #cdd6f4;
  border: none;
  border-radius: 0;
  line-height: 1.6;
  padding: 8px 12px;
}
.prompt-footer { display: flex; justify-content: flex-end; margin-top: 8px; }

.tab-bottom-note {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #86909c;
  padding: 10px 16px 14px;
}

/* ── Model cards ── */
.model-card {
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 14px;
}
.model-card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.model-card-title  { font-weight: 600; color: #1d2129; font-size: 14px; }
.model-card-desc   { font-size: 12px; color: #86909c; margin-top: 8px; }
.slider-row { display: flex; align-items: center; gap: 12px; }
.param-hint { margin-left: 12px; font-size: 12px; color: #86909c; }

/* ── Prompt blocks ── */
.prompt-grid {
  display: grid;
  gap: 16px;
}
.prompt-config-card {
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 16px;
}
.prompt-config-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 10px;
}
.prompt-config-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 4px;
}
.prompt-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.prompt-block { margin-bottom: 24px; }
.prompt-label { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.prompt-hint  { font-size: 12px; color: #86909c; }
.prompt-editor-full :deep(.el-textarea__inner) {
  font-family: 'Courier New', monospace; font-size: 13px;
  background: #1e1e2e; color: #cdd6f4; border-color: #313244; line-height: 1.6;
}

/* ── Tool and safety settings ── */
.tool-config-layout {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) 360px;
  gap: 16px;
}
.tool-config-list {
  display: grid;
  gap: 10px;
}
.tool-config-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
}
.tool-key {
  margin-top: 4px;
  font-size: 11px;
  color: #a8b0bd;
  font-family: 'Courier New', monospace;
}
.tool-model-panel,
.safety-card {
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  padding: 16px;
}
.safety-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.tab-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
}

/* ── Right sidebar ── */
.settings-sidebar {
  width: 230px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sb-section {
  background: white;
  border-radius: 12px;
  border: 1px solid #f0f2f5;
  padding: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.sb-title { font-size: 13px; font-weight: 600; color: #1d2129; margin-bottom: 10px; }
.config-status-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: #f0fdf4;
  border: 1px solid #d9f7be;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.cs-info {}
.cs-label { font-size: 13px; font-weight: 600; }
.cs-sub   { font-size: 11px; color: #86909c; margin-top: 2px; }
.sb-kv { display: flex; align-items: center; gap: 6px; padding: 5px 0; border-bottom: 1px dashed #f5f7fa; }
.sb-kv:last-child { border-bottom: none; }
.sbk { font-size: 12px; color: #86909c; min-width: 58px; }
.sbv { font-size: 12px; color: #4e5969; flex: 1; }

.quick-ops { display: flex; flex-direction: column; gap: 6px; }
.qo-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  transition: background .15s;
  border: 1px solid #f0f2f5;
}
.qo-btn:hover { background: #f0f5ff; color: #1677ff; border-color: #d4e4ff; }

@media (max-width: 1180px) {
  .settings-body,
  .tool-config-layout,
  .safety-grid {
    grid-template-columns: 1fr;
    display: grid;
  }
  .settings-sidebar {
    width: 100%;
  }
}
</style>
