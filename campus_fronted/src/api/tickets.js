import http from './http'

export const ticketsApi = {
  list:        (params)          => http.get('/tickets', { params }),
  stats:       ()                => http.get('/tickets/stats'),
  getById:     (id)              => http.get(`/tickets/${id}`),
  getMessages: (id)              => http.get(`/tickets/${id}/messages`),
  reply:       (id, content)     => http.post(`/tickets/${id}/reply`, { content }),
  resolve:     (id)              => http.put(`/tickets/${id}/resolve`),
  close:       (id)              => http.put(`/tickets/${id}/close`),
  rate:        (id, rating)      => http.put(`/tickets/${id}/rate`, { rating }),
  remove:      (id)              => http.delete(`/tickets/${id}`),
}
