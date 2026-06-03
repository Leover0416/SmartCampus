import axios from 'axios'
import http from './http'

function authHeaders() {
  const token = localStorage.getItem('campus_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const knowledgeApi = {
  list(params) {
    return http.get('/knowledge/docs', { params })
  },

  upload(formData, onProgress) {
    return http.post('/knowledge/docs/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
      onUploadProgress: onProgress
    })
  },

  delete(docId) {
    return http.delete(`/knowledge/docs/${docId}`)
  },

  reindex(docId) {
    return http.post(`/knowledge/docs/${docId}/reindex`)
  },

  searchTest(query, topK = 10) {
    return http.get('/knowledge/docs/search-test', { params: { query, topK } })
  },

  async previewBlob(docId) {
    const res = await axios.get(`/api/v1/knowledge/docs/${docId}/preview`, {
      responseType: 'blob',
      headers: authHeaders()
    })
    return res.data
  },

  async downloadBlob(docId) {
    const res = await axios.get(`/api/v1/knowledge/docs/${docId}/download`, {
      responseType: 'blob',
      headers: authHeaders()
    })
    return res.data
  },

  async previewText(docId) {
    const res = await axios.get(`/api/v1/knowledge/docs/${docId}/preview-text`, {
      responseType: 'text',
      headers: authHeaders()
    })
    return res.data
  },

  categories() {
    return http.get('/knowledge/categories')
  },

  createCategory(data) {
    return http.post('/knowledge/categories', data)
  },

  deleteCategory(code) {
    return http.delete(`/knowledge/categories/${encodeURIComponent(code)}`)
  }
}
