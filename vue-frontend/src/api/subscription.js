import api from './index.js'

// 구독 시 모든 라이선스 가격에 적용되는 할인율 (백엔드와 반드시 동일해야 함)
export const SUBSCRIPTION_DISCOUNT_RATE = 0.3

export const subscriptionApi = {
  getInstructorProfile(instructorId) {
    return api.get(`/api/users/${instructorId}`)
  },
  setMySubscriptionPrice(monthlyPrice) {
    return api.put('/api/users/me/subscription-price', { monthlyPrice })
  },
  subscribe(instructorId) {
    return api.post('/api/subscriptions', { instructorId })
  },
  getMySubscriptions() {
    return api.get('/api/subscriptions/me')
  }
}

export function applyDiscount(price) {
  const value = Number(price ?? 0)
  if (Number.isNaN(value)) return 0
  return Math.round(value * (1 - SUBSCRIPTION_DISCOUNT_RATE))
}