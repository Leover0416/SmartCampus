<template>
  <div class="auth-root">

    <!-- ── Full-page wrapper ── -->
    <div class="auth-page">

      <!-- LEFT: illustration + copy -->
      <div class="left-panel">
        <div class="left-top-logo">
          <el-icon :size="28" color="#2563eb"><School /></el-icon>
          <span class="logo-text">SmartCampus <em>校园智能助理</em></span>
        </div>

        <div class="hero-copy">
          <h1 class="hero-title">
            <span class="blue">SmartCampus</span> 校园智能助理
          </h1>
          <p class="hero-sub">懂校园 · 更智能 · 助力师生高效学习与工作</p>
        </div>

        <div class="features">
          <div class="feat-item" v-for="f in features" :key="f.title">
            <div class="feat-icon"><el-icon :size="20" color="#2563eb"><component :is="f.icon" /></el-icon></div>
            <div>
              <div class="feat-title">{{ f.title }}</div>
              <div class="feat-desc">{{ f.desc }}</div>
            </div>
          </div>
        </div>

        <!-- Decorative illustration -->
        <div class="illustration">
          <div class="illus-bg-circle c1"></div>
          <div class="illus-bg-circle c2"></div>
          <div class="illus-robot">
            <div class="robot-head">
              <div class="robot-eye"></div>
              <div class="robot-eye"></div>
            </div>
            <div class="robot-body">
              <el-icon :size="28" color="#2563eb"><School /></el-icon>
            </div>
          </div>
          <div class="illus-card card-left">
            <el-icon color="#2563eb"><ChatDotRound /></el-icon>
            <span>AI 问答</span>
          </div>
          <div class="illus-card card-right">
            <el-icon color="#2563eb"><Document /></el-icon>
            <span>知识库</span>
          </div>
        </div>
      </div>

      <!-- RIGHT: login card -->
      <div class="right-panel">
        <div class="auth-card">
          <!-- Tabs -->
          <div class="auth-tabs">
            <button class="tab-btn" :class="{ active: tab === 'login' }" @click="tab = 'login'">登录</button>
            <button class="tab-btn" :class="{ active: tab === 'register' }" @click="tab = 'register'">注册</button>
          </div>

          <!-- Account login -->
          <el-form v-if="tab === 'login'" ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="学号 / 工号 / 账号" clearable>
                <template #prefix><el-icon class="input-icon"><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码" show-password @keyup.enter="handleLogin">
                <template #prefix><el-icon class="input-icon"><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <div class="row-between">
              <el-checkbox v-model="rememberMe">记住我</el-checkbox>
              <span class="link-text" @click="openResetPasswordDialog">忘记密码</span>
            </div>
            <el-button type="primary" :loading="loading" class="submit-btn" @click="handleLogin">登录</el-button>
          </el-form>

          <!-- Register -->
          <el-form v-if="tab === 'register'" ref="registerFormRef" :model="registerForm" :rules="registerRules" size="large" @submit.prevent="handleRegister">
            <el-form-item prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="姓名" clearable>
                <template #prefix><el-icon class="input-icon"><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="username">
              <el-input v-model="registerForm.username" placeholder="学号 / 工号 / 账号" clearable>
                <template #prefix><el-icon class="input-icon"><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="email">
              <el-input v-model="registerForm.email" placeholder="邮箱" clearable>
                <template #prefix><el-icon class="input-icon"><Message /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="role">
              <el-select v-model="registerForm.role" placeholder="选择身份" style="width:100%">
                <el-option label="学生" value="STUDENT" />
                <el-option label="教师" value="TEACHER" />
              </el-select>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="设置密码" show-password>
                <template #prefix><el-icon class="input-icon"><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" show-password @keyup.enter="handleRegister">
                <template #prefix><el-icon class="input-icon"><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">注册</el-button>
          </el-form>

          <p class="switch-link">
            <template v-if="tab === 'login'">
              还没有账号？<span class="link-text" @click="tab = 'register'">立即注册</span>
            </template>
            <template v-else>
              已有账号？<span class="link-text" @click="tab = 'login'">立即登录</span>
            </template>
          </p>

          <!-- Demo hint -->
          <div class="demo-hint">
            演示账号：admin，密码为 123456
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="resetPasswordDialogVisible"
      title="重置密码"
      width="420px"
      class="reset-password-dialog"
      @closed="resetForgotPasswordForm"
    >
      <el-alert
        title="请输入注册时使用的用户名和邮箱，验证通过后密码将重置为 123456。"
        type="info"
        :closable="false"
        show-icon
        class="reset-password-tip"
      />
      <el-form
        ref="resetPasswordFormRef"
        :model="resetPasswordForm"
        :rules="resetPasswordRules"
        size="large"
        label-position="top"
        @submit.prevent="handleResetPassword"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="resetPasswordForm.username" placeholder="学号 / 工号 / 账号" clearable>
            <template #prefix><el-icon class="input-icon"><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="注册邮箱" prop="email">
          <el-input v-model="resetPasswordForm.email" placeholder="请输入注册邮箱" clearable @keyup.enter="handleResetPassword">
            <template #prefix><el-icon class="input-icon"><Message /></el-icon></template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPasswordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetPasswordLoading" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- Footer -->
    <footer class="page-footer">
      <span><el-icon :size="12"><Lock /></el-icon> 安全可靠 · 数据隐私保护</span>
      <span>© 2025 SmartCampus 校园智能助理 版权所有</span>
      <span>明ICP备20250001号-1</span>
      <span>隐私政策</span>
      <span>使用条款</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock, Message, School, ChatDotRound, Document } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route  = useRoute()
const auth   = useAuthStore()

const tab        = ref('login')
const loading    = ref(false)
const resetPasswordLoading = ref(false)
const resetPasswordDialogVisible = ref(false)
const REMEMBERED_USERNAME_KEY = 'campus_remembered_username'
const rememberMe = ref(!!localStorage.getItem(REMEMBERED_USERNAME_KEY))
const formRef    = ref()
const registerFormRef = ref()
const resetPasswordFormRef = ref()

const form = reactive({ username: localStorage.getItem(REMEMBERED_USERNAME_KEY) || '', password: '' })
const resetPasswordForm = reactive({ username: '', email: '' })
const registerForm = reactive({
  nickname: '',
  username: '',
  email: '',
  role: 'STUDENT',
  password: '',
  confirmPassword: ''
})
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const resetPasswordRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 2, max: 30, message: '账号长度 2-30 位', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}
const registerRules = {
  nickname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 2, max: 30, message: '账号长度 2-30 位', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择身份', trigger: 'change' }],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_, value, callback) => {
        if (value !== registerForm.password) callback(new Error('两次密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const features = [
  { icon: 'ChatDotRound', title: '校园智能问答',   desc: '7×24 小时智能解答校园各类问题' },
  { icon: 'List',         title: '教务服务查询',   desc: '课表、成绩、考试等一站式便捷查询' },
  { icon: 'Search',       title: '知识库检索',     desc: '汇聚校园知识资源精准检索与获取' },
  { icon: 'User',         title: '老师协作处理',   desc: '高效协同，流程闭环提升服务效率' },
]

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login(form)
    if (rememberMe.value) {
      localStorage.setItem(REMEMBERED_USERNAME_KEY, form.username)
    } else {
      localStorage.removeItem(REMEMBERED_USERNAME_KEY)
    }
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/chat')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.register({
      username: registerForm.username,
      email: registerForm.email,
      password: registerForm.password,
      nickname: registerForm.nickname,
      role: registerForm.role
    })
    ElMessage.success('注册成功')
    router.push(route.query.redirect || '/chat')
  } finally {
    loading.value = false
  }
}

function openResetPasswordDialog() {
  resetPasswordForm.username = form.username
  resetPasswordDialogVisible.value = true
}

function resetForgotPasswordForm() {
  resetPasswordForm.username = ''
  resetPasswordForm.email = ''
  resetPasswordFormRef.value?.clearValidate()
}

async function handleResetPassword() {
  const valid = await resetPasswordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  resetPasswordLoading.value = true
  try {
    await auth.resetPassword({
      username: resetPasswordForm.username,
      email: resetPasswordForm.email
    })
    form.username = resetPasswordForm.username
    form.password = ''
    ElMessage.success('密码已重置为 123456，请重新登录')
    resetPasswordDialogVisible.value = false
  } finally {
    resetPasswordLoading.value = false
  }
}
</script>

<style scoped>
* { box-sizing: border-box; }

.auth-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(140deg, #dbeafe 0%, #eff6ff 40%, #e0ecff 100%);
}

/* ── Main split area ── */
.auth-page {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 40px 48px 20px;
  gap: 120px;
}

/* ── Left ── */
.left-panel {
  flex: 0 1 560px;
  display: flex;
  flex-direction: column;
  gap: 28px;
  position: relative;
  padding-right: 0;
}

.left-top-logo {
  display: flex;
  align-items: center;
  gap: 8px;
}
.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}
.logo-text em { font-style: normal; color: #64748b; font-weight: 400; margin-left: 4px; }

.hero-copy { margin-top: 8px; }
.hero-title {
  font-size: 34px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1.2;
  margin: 0 0 12px;
}
.hero-title .blue { color: #2563eb; }
.hero-sub { font-size: 15px; color: #64748b; margin: 0; }

.features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 480px;
}
.feat-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(255,255,255,.55);
  border-radius: 10px;
  padding: 12px 14px;
  backdrop-filter: blur(4px);
}
.feat-icon {
  width: 36px; height: 36px;
  background: #eff6ff;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.feat-title { font-size: 13px; font-weight: 600; color: #1e293b; }
.feat-desc  { font-size: 11px; color: #64748b; margin-top: 2px; line-height: 1.4; }

/* Illustration */
.illustration {
  position: relative;
  height: 200px;
  margin-top: -10px;
}
.illus-bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(37,99,235,.08);
}
.c1 { width: 260px; height: 260px; left: 60px; top: -40px; }
.c2 { width: 180px; height: 180px; left: 20px; top: 20px; background: rgba(37,99,235,.05); }

.illus-robot {
  position: absolute;
  left: 160px; top: 20px;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.robot-head {
  width: 60px; height: 50px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  border-radius: 14px;
  display: flex; align-items: center; justify-content: center; gap: 10px;
}
.robot-eye {
  width: 12px; height: 12px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(255,255,255,.3);
}
.robot-body {
  width: 52px; height: 60px;
  background: linear-gradient(135deg, #60a5fa, #2563eb);
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
}

.illus-card {
  position: absolute;
  background: #fff;
  border-radius: 10px;
  padding: 8px 14px;
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 600; color: #1e293b;
  box-shadow: 0 4px 16px rgba(37,99,235,.12);
}
.card-left  { left: 30px; top: 60px; }
.card-right { left: 270px; top: 40px; }

/* ── Right ── */
.right-panel {
  flex-shrink: 0;
  width: 400px;
}

.auth-card {
  background: #fff;
  border-radius: 16px;
  padding: 36px 36px 28px;
  box-shadow: 0 20px 60px rgba(37,99,235,.12);
}

/* Tabs */
.auth-tabs {
  display: flex;
  border-bottom: 2px solid #f1f5f9;
  margin-bottom: 24px;
}
.tab-btn {
  flex: 1;
  background: none;
  border: none;
  padding: 10px 0;
  font-size: 15px;
  font-weight: 500;
  color: #94a3b8;
  cursor: pointer;
  position: relative;
  transition: color .2s;
}
.tab-btn.active {
  color: #2563eb;
  font-weight: 700;
}
.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -2px; left: 20%; right: 20%;
  height: 2px;
  background: #2563eb;
  border-radius: 2px;
}

/* Inputs */
.input-icon { color: #94a3b8; }
:deep(.el-input__wrapper) {
  background: #f8fafc;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  border-radius: 8px;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #2563eb inset !important;
}

.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -4px 0 16px;
}

.sms-row { display: flex; gap: 8px; width: 100%; }
.sms-row .el-input { flex: 1; }
.get-code-btn { flex-shrink: 0; white-space: nowrap; }

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(90deg, #2563eb, #3b82f6);
  border: none;
  border-radius: 8px;
  margin-top: 4px;
}
.submit-btn:hover { background: linear-gradient(90deg, #1d4ed8, #2563eb); }

.switch-link {
  text-align: center;
  margin: 18px 0 0;
  font-size: 13px;
  color: #64748b;
}
.link-text { color: #2563eb; cursor: pointer; font-weight: 500; text-decoration: none; }
.link-text:hover { text-decoration: underline; }

.demo-hint {
  margin-top: 14px;
  padding: 8px 12px;
  background: #f0f9ff;
  border-radius: 8px;
  font-size: 11px;
  color: #64748b;
  text-align: center;
  border: 1px solid #bae6fd;
}

.reset-password-tip {
  margin-bottom: 18px;
  line-height: 1.5;
}

/* ── Footer ── */
.page-footer {
  padding: 12px 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  font-size: 12px;
  color: #94a3b8;
  border-top: 1px solid rgba(37,99,235,.08);
  background: rgba(255,255,255,.4);
  flex-wrap: wrap;
}
.page-footer .el-icon { vertical-align: -2px; margin-right: 3px; }
</style>
