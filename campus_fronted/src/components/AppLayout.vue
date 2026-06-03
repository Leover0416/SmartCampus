<template>
  <div class="layout">
    <!-- ── 顶部通栏 Header ── -->
    <header class="top-header">
      <div class="header-brand">
        <div class="brand-icon-wrap">
          <svg width="30" height="30" viewBox="0 0 30 30" fill="none">
            <circle cx="15" cy="15" r="14" fill="#e6f0ff"/>
            <rect x="7" y="9" width="16" height="14" rx="5" fill="#1677ff"/>
            <circle cx="11" cy="15" r="2.4" fill="white"/>
            <circle cx="19" cy="15" r="2.4" fill="white"/>
            <circle cx="11.7" cy="15" r="1" fill="#1677ff"/>
            <circle cx="19.7" cy="15" r="1" fill="#1677ff"/>
            <rect x="10" y="19" width="10" height="2.2" rx="1.1" fill="white" opacity="0.9"/>
            <rect x="4.5" y="12" width="2.5" height="7" rx="1.25" fill="#1677ff" opacity="0.45"/>
            <rect x="23" y="12" width="2.5" height="7" rx="1.25" fill="#1677ff" opacity="0.45"/>
          </svg>
        </div>
        <span class="brand-name">SmartCampus 校园智能助理</span>
      </div>

      <div class="header-right">
        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="user-area">
            <el-avatar :size="32" :style="{ background: '#1677ff', fontSize: '13px', fontWeight: 600 }">
              {{ nickname.charAt(0) }}
            </el-avatar>
            <span class="username-text">{{ nickname }}</span>
            <el-icon :size="12" style="color:#86909c"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="changePassword">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- ── 主体区域 ── -->
    <div class="layout-body">
      <aside class="sidebar">
        <nav class="nav">
          <router-link
            v-for="item in visibleMenus"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
          >
            <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <!-- 底部宣传卡 -->
        <div class="promo-card">
          <div class="promo-info">
            <div class="promo-title">SmartCampus 3.0</div>
            <div class="promo-sub">更智能的校园服务体验</div>
<br>
          </div>
          <div class="promo-robot">
            <svg width="54" height="54" viewBox="0 0 54 54" fill="none">
              <circle cx="27" cy="27" r="25" fill="#1677ff" opacity="0.1"/>
              <rect x="11" y="16" width="32" height="24" rx="9" fill="#1677ff" opacity="0.85"/>
              <circle cx="20" cy="26" r="4" fill="white"/>
              <circle cx="34" cy="26" r="4" fill="white"/>
              <circle cx="21" cy="26" r="1.6" fill="#1677ff"/>
              <circle cx="35" cy="26" r="1.6" fill="#1677ff"/>
              <rect x="18" y="32" width="18" height="3.5" rx="1.75" fill="white" opacity="0.85"/>
              <rect x="5" y="22" width="5" height="11" rx="2.5" fill="#1677ff" opacity="0.4"/>
              <rect x="44" y="22" width="5" height="11" rx="2.5" fill="#1677ff" opacity="0.4"/>
              <rect x="20" y="6" width="6" height="10" rx="3" fill="#1677ff" opacity="0.55"/>
              <rect x="28" y="6" width="6" height="10" rx="3" fill="#1677ff" opacity="0.55"/>
            </svg>
          </div>
        </div>
      </aside>

      <main class="main-content">
        <router-view />
      </main>
    </div>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="420px" @closed="resetPasswordForm">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="88px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingPassword" @click="submitChangePassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const route  = useRoute()
const auth   = useAuthStore()

const nickname = computed(() => auth.nickname)
const passwordDialogVisible = ref(false)
const changingPassword = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '新密码长度应为6-50位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const allMenus = [
  { path: '/chat',      label: '对话窗口',   icon: 'ChatDotRound',   roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  { path: '/tickets',   label: '老师工作台', icon: 'Headset',        roles: ['TEACHER', 'ADMIN'] },
  { path: '/knowledge', label: '知识库管理', icon: 'FolderOpened',   roles: ['TEACHER', 'ADMIN'] },
  { path: '/faq',       label: 'FAQ管理',    icon: 'QuestionFilled', roles: ['TEACHER', 'ADMIN'] },
  { path: '/dashboard', label: '数据看板',   icon: 'DataAnalysis',   roles: ['TEACHER', 'ADMIN'] },
  { path: '/settings',  label: '系统设置',   icon: 'Setting',        roles: ['TEACHER', 'ADMIN'] },
]

const visibleMenus = computed(() => allMenus.filter(m => m.roles.includes(auth.role)))

function isActive(path) { return route.path.startsWith(path) }

function handleUserCommand(command) {
  if (command === 'changePassword') passwordDialogVisible.value = true
  if (command === 'logout') handleLogout()
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  changingPassword.value = false
  passwordFormRef.value?.clearValidate()
}

async function submitChangePassword() {
  await passwordFormRef.value?.validate()
  changingPassword.value = true
  try {
    await auth.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    passwordDialogVisible.value = false
    auth.logout()
    router.push('/login')
  } finally {
    changingPassword.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    auth.logout()
    router.push('/login')
  } catch {}
}
</script>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

/* ── Header ── */
.top-header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e8ecf0;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 20px;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
  z-index: 100;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 190px;
  flex-shrink: 0;
}
.brand-icon-wrap { display: flex; align-items: center; }
.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: #1d2129;
  letter-spacing: .2px;
  white-space: nowrap;
}

.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background .15s;
}
.user-area:hover { background: #f5f7fa; }
.username-text { font-size: 14px; color: #1d2129; font-weight: 500; }

/* ── Layout body ── */
.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ── Sidebar ── */
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e8ecf0;
  display: flex;
  flex-direction: column;
  padding: 10px 0 0;
}

.nav { flex: 1; padding: 0 10px; overflow-y: auto; }

.nav-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 14px;
  color: #4e5969;
  text-decoration: none;
  border-radius: 8px;
  margin-bottom: 2px;
  font-size: 14px;
  transition: all .15s;
}
.nav-item:hover { background: #f0f5ff; color: #1677ff; }
.nav-item.active { background: #1677ff; color: #fff; font-weight: 500; }
.nav-icon { font-size: 18px; flex-shrink: 0; }

/* ── Promo card ── */
.promo-card {
  margin: 12px 10px;
  background: linear-gradient(135deg, #eaf1ff 0%, #d4e4ff 100%);
  border-radius: 12px;
  padding: 14px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}
.promo-info { flex: 1; min-width: 0; }
.promo-title { font-size: 13px; font-weight: 700; color: #1677ff; line-height: 1.3; }
.promo-sub { font-size: 11px; color: #4e5969; margin: 2px 0 4px; line-height: 1.4; }
.promo-cta { font-size: 11px; color: #1677ff; font-weight: 500; text-decoration: none; }
.promo-cta:hover { text-decoration: underline; }
.promo-robot { flex-shrink: 0; }

/* ── Main content ── */
.main-content {
  flex: 1;
  overflow: auto;
  background: #f5f7fa;
}
</style>
