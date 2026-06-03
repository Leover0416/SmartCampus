import http from './http'

export const faqApi = {
  list:        (params)     => http.get('/faq', { params }),
  categories:  ()           => http.get('/faq/categories'),
  top:         (n = 10)     => http.get('/faq/top', { params: { n } }),
  suggestions: (params)     => http.get('/faq/suggestions', { params }),
  getById:     (id)         => http.get(`/faq/${id}`),
  create:      (data)       => http.post('/faq', data),
  update:      (id, data)   => http.put(`/faq/${id}`, data),
  remove:      (id)         => http.delete(`/faq/${id}`),
  toggle:      (id)         => http.put(`/faq/${id}/toggle`),
  batchImport: (data)       => http.post('/faq/import', data),
  export:      ()           => http.get('/faq/export'),
}
