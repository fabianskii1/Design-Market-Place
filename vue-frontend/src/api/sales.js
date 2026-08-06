import api from './index.js'

export const salesApi = {
  getMySales() {
    return api.get('/api/designs/sales/me')
  }
}