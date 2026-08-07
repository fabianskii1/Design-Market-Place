import api from './index.js'
import { salesApi } from '@/api/sales.js'


export const salesApi = {
  getMySales() {
    return api.get('/api/courses/sales/me')
  }
}