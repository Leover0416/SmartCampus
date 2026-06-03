<template>
  <div class="auth-root">

    <div class="auth-page">
      <!-- LEFT -->
      <div class="left-panel">
        <div class="left-top-logo">
          <el-icon :size="28" color="#2563eb"><School /></el-icon>
          <span class="logo-text">SmartCampus <em>校园智能助理</em></span>
        </div>

        <div class="hero-copy">
          <h1 class="hero-title">
            加入 <span class="blue">SmartCampus</span>，<br>开启智能校园服务体验
          </h1>
          <p class="hero-sub">为学生和教师提供高效、便捷、智能的校园服务体验</p>
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

      <!-- RIGHT -->
      <div class="right-panel">
        <div class="auth-card">
          <!-- Tabs -->
          <div class="auth-tabs">
            <button class="tab-btn" :class="{ active: tab === 'student' }" @click="tab = 'student'">学生注册</button>
            <button class="tab-btn" :class="{ active: tab === 'teacher' }" @click="tab = 'teacher'">教师注册</button>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" size="large">

            <el-form-item prop="nickname">
              <el-input v-model="form.nickname" placeholder="姓名">
                <template #prefix><el-icon class="input-icon"><User /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item prop="username">
              <el-input v-model="form.username" :placeholder="tab === 'student' ? '学号' : '工号'">
                <template #prefix><el-icon class="input-icon"><UserFilled /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item prop="phone">
              <el-input v-model="form.phone" placeholder="手机号">
                <template #prefix><el-icon class="input-icon"><Iphone /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item prop="code">
              <div class="code-row">
                <el-input v-model="form.code" placeholder="验证码">
                  <template #prefix><el-icon class="input-icon"><Key /></el-icon></template>
                </el-input>
                <span class="get-code" @click="handleGetCode" :class="{ disabled: codeCooling > 0 }">
                  {{ codeCooling > 0 ? codeCooling + 's 后重试' : '获取验证码' }}
                </span>
              </div>
            </el-form-item>

            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="设置密码（8-20位，包含字母、数字或符号）" show-password>
                <template #prefix><el-icon class="input-icon"><Lock /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" show-password>
                <template #prefix><el-icon class="input-icon"><Lock /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item prop="agreed">
              <el-checkbox v-model="form.agreed">
                我已阅读并同意
                <span class="link-text">《用户协议》</span>
                和
                <span class="link-text">《隐私政策》</span>
              </el-checkbox>
            </el-form-item>

            <el-button type="primary" :loading="loading" class="submit-btn" @click="handleRegister">注册</el-button>
          </el-form>

          <p class="switch-link">
            已有账号？<router-link to="/login" class="link-text">立即登录</router-link>
          </p>

          <div class="divider-or"><span>或</span></div>

          <button class="sso-btn">
            <el-icon><Lock /></el-icon>
            统一身份认证开通
          </button>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <footer class="page-footer">
      <span><el-icon :size="12"><Lock /></el-icon> 安全可靠，数据隐私保护</span>
      <span>© 2025 SmartCampus 校园智能助理 版权所有</span>
      <span>浙ICP备20250001号-1</span>
      <span>隐私政策</span>
      <span>用户协议</span>
      <span>帮助中心</span>
      <span>联系我们</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { User, UserFilled, Lock, Key, Iphone, School, ChatDotRound, Document } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth   = useAuthStore()

const tab        = ref('student')
const loading    = ref(false)
const formRef    = ref()
const codeCooling = ref(0)

const form = reactive({
  nickname: '', username: '', phone: '', code: '',
  password: '', confirmPassword: '', agreed: false
})

const rules = {
  nickname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入学号/工号', trigger: 'blur' }],
  phone:    [{ required: true, message: '请输入手机号', trigger: 'blur' },
             { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请设置密码', trigger: 'blur' },
             { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, val, cb) => {
        if (val !== form.password) cb(new Error('两次密码不一致'))
        else cb()
      }, trigger: 'blur'
    }
  ],
  agreed: [{ validator: (rule, val, cb) => val ? cb() : cb(new Error('请阅读并同意用户协议')), trigger: 'change' }]
}

const features = [
  { icon: 'ChatDotRound', title: '智能问答', desc: '7×24 小时解答校园疑问与咨询' },
  { icon: 'List',         title: '教务查询', desc: '课表、成绩、考试等一站式便捷查询' },
  { icon: 'Search',       title: '文件检索', desc: '快速查找校内文件资料与知识库' },
  { icon: 'User',         title: '老师协作', desc: '高效协同，助力教学与科研工作' },
]

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.register({
      username: form.username,
      nickname: form.nickname,
      password: form.password,
      email:    form.phone + '@campus.edu',
      role:     tab.value === 'teacher' ? 'TEACHER' : 'STUDENT'
    })
    ElMessage.success('注册成功，欢迎加入 SmartCampus！')
    router.push('/chat')
  } finally {
    loading.value = false
  }
}

function handleGetCode() {
  if (!form.phone) { ElMessage.warning('请先输入手机号'); return }
  ElMessage.success('验证码已发送（演示）')
  codeCooling.value = 60
  const timer = setInterval(() => {
    codeCooling.value--
    if (codeCooling.value <= 0) clearInterval(timer)
  }, 1000)
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

.auth-page {
  flex: 1;
  display: flex;
  align-items: center;
  padding: 32px 60px 16px;
  gap: 0;
  overflow: hidden;
}

/* ── Left ── */
.left-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding-right: 40px;
}

.left-top-logo { display: flex; align-items: center; gap: 8px; }
.logo-text { font-size: 16px; font-weight: 700; color: #1e293b; }
.logo-text em { font-style: normal; color: #64748b; font-weight: 400; margin-left: 4px; }

.hero-title {
  font-size: 32px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1.25;
  margin: 0 0 10px;
}
.hero-title .blue { color: #2563eb; }
.hero-sub { font-size: 14px; color: #64748b; margin: 0; }

.features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  max-width: 460px;
}
.feat-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(255,255,255,.55);
  border-radius: 10px;
  padding: 10px 12px;
}
.feat-icon {
  width: 34px; height: 34px;
  background: #eff6ff;
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.feat-title { font-size: 13px; font-weight: 600; color: #1e293b; }
.feat-desc  { font-size: 11px; color: #64748b; margin-top: 1px; line-height: 1.4; }

.illustration {
  position: relative;
  height: 160px;
}
.illus-bg-circle { position: absolute; border-radius: 50%; background: rgba(37,99,235,.08); }
.c1 { width: 220px; height: 220px; left: 60px; top: -50px; }
.c2 { width: 160px; height: 160px; left: 20px; top: -10px; background: rgba(37,99,235,.05); }
.illus-robot {
  position: absolute; left: 150px; top: 10px;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.robot-head {
  width: 56px; height: 46px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  border-radius: 13px;
  display: flex; align-items: center; justify-content: center; gap: 9px;
}
.robot-eye {
  width: 11px; height: 11px; background: #fff; border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(255,255,255,.3);
}
.robot-body {
  width: 48px; height: 56px;
  background: linear-gradient(135deg, #60a5fa, #2563eb);
  border-radius: 11px;
  display: flex; align-items: center; justify-content: center;
}
.illus-card {
  position: absolute;
  background: #fff; border-radius: 10px; padding: 7px 12px;
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 600; color: #1e293b;
  box-shadow: 0 4px 16px rgba(37,99,235,.12);
}
.card-left  { left: 20px; top: 50px; }
.card-right { left: 255px; top: 30px; }

/* ── Right ── */
.right-panel { flex-shrink: 0; width: 400px; }

.auth-card {
  background: #fff;
  border-radius: 16px;
  padding: 28px 32px 24px;
  box-shadow: 0 20px 60px rgba(37,99,235,.12);
}

.auth-tabs {
  display: flex;
  border-bottom: 2px solid #f1f5f9;
  margin-bottom: 18px;
}
.tab-btn {
  flex: 1; background: none; border: none;
  padding: 8px 0; font-size: 15px; font-weight: 500;
  color: #94a3b8; cursor: pointer; position: relative; transition: color .2s;
}
.tab-btn.active { color: #2563eb; font-weight: 700; }
.tab-btn.active::after {
  content: ''; position: absolute; bottom: -2px; left: 20%; right: 20%;
  height: 2px; background: #2563eb; border-radius: 2px;
}

.input-icon { color: #94a3b8; }
:deep(.el-input__wrapper) {
  background: #f8fafc;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  border-radius: 8px;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #2563eb inset !important;
}
:deep(.el-form-item) { margin-bottom: 12px; }

.code-row { display: flex; gap: 10px; align-items: center; width: 100%; }
.code-row .el-input { flex: 1; }
.get-code {
  flex-shrink: 0; white-space: nowrap;
  font-size: 13px; color: #2563eb; cursor: pointer; font-weight: 500;
}
.get-code.disabled { color: #94a3b8; cursor: not-allowed; }
.get-code:hover:not(.disabled) { text-decoration: underline; }

.submit-btn {
  width: 100%; height: 42px;
  font-size: 15px; font-weight: 600;
  background: linear-gradient(90deg, #2563eb, #3b82f6);
  border: none; border-radius: 8px; margin-top: 2px;
}
.submit-btn:hover { background: linear-gradient(90deg, #1d4ed8, #2563eb); }

.switch-link {
  text-align: center; margin: 14px 0 0;
  font-size: 13px; color: #64748b;
}
.link-text { color: #2563eb; cursor: pointer; font-weight: 500; text-decoration: none; }
.link-text:hover { text-decoration: underline; }

.divider-or {
  text-align: center; margin: 14px 0;
  position: relative; color: #94a3b8; font-size: 13px;
}
.divider-or::before, .divider-or::after {
  content: ''; position: absolute; top: 50%;
  width: calc(50% - 18px); height: 1px; background: #e2e8f0;
}
.divider-or::before { left: 0; }
.divider-or::after  { right: 0; }

.sso-btn {
  width: 100%; height: 40px;
  border: 1px solid #e2e8f0; border-radius: 8px;
  background: #fff; color: #475569; font-size: 14px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; gap: 6px;
  transition: all .2s;
}
.sso-btn:hover { border-color: #2563eb; color: #2563eb; background: #eff6ff; }

/* ── Footer ── */
.page-footer {
  padding: 10px 60px;
  display: flex; align-items: center; justify-content: center;
  gap: 20px; font-size: 12px; color: #94a3b8;
  border-top: 1px solid rgba(37,99,235,.08);
  background: rgba(255,255,255,.4); flex-wrap: wrap;
}
.page-footer .el-icon { vertical-align: -2px; margin-right: 3px; }
</style>
