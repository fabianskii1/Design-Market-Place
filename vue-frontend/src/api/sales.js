import api from './index.js'

export const salesApi = {
  getMySales() {
    return api.get('/api/courses/sales/me')
  }
}