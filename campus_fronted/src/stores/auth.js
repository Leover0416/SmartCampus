/**
 * 【导读】登录状态（Pinia）。
 * token / 用户信息存在 localStorage 的 campus_token、campus_user。
 * 同一浏览器只能存一个账号，后登录会覆盖（多角色演示请用多个浏览器）。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('campus_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('campus_user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role || '')
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isTeacher = computed(() => role.value === 'TEACHER' || role.value === 'ADMIN')

  function setAuth(data) {
    token.value = data.token
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      role: data.role,
      nickname: data.nickname,
      avatarUrl: data.avatarUrl
    }
    localStorage.setItem('campus_token', data.token)
    localStorage.setItem('campus_user', JSON.stringify(userInfo.value))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('campus_token')
    localStorage.removeItem('campus_user')
  }

  async function login(credentials) {
    const res = await authApi.login(credentials)
    setAuth(res.data)
    return res
  }

  async function register(data) {
    const res = await authApi.register(data)
    setAuth(res.data)
    return res
  }

  async function changePassword(data) {
    return authApi.changePassword(data)
  }

  async function resetPassword(data) {
    return authApi.resetPassword(data)
  }

  return { token, userInfo, isLoggedIn, role, username, nickname, isAdmin, isTeacher, login, register, changePassword, resetPassword, logout }
})
