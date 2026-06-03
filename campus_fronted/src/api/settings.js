import http from './http'

export const settingsApi = {
  list:   () => http.get('/settings/configs'),
  update: (data) => http.put('/settings/configs', data),
  reset:  () => http.post('/settings/configs/reset'),
  export: () => http.get('/settings/configs/export'),
  import: (data) => http.post('/settings/configs/import', data),
}
