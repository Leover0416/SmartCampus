import http from './http'

export const dashboardApi = {
  get: (days = 7) => http.get('/dashboard', { params: { days } }),
}
