import api from './index.js'

/**
 * 실제 백엔드(payment-service) 정책: 디자이너별 가격 설정이 아니라
 * "구독 = 월 정액 9,900원, 전체 30% 할인" 고정 정책이다.
 * (payment-service application.yml: subscription.monthly-price / subscription.discount-rate)
 * 값을 바꿀 수 있는 API는 없고, 두 상수는 화면 표시용 추정치일 뿐이며
 * 실제 계산과 청구는 항상 서버에서 이뤄진다.
 */
export const SUBSCRIPTION_MONTHLY_PRICE = 9900
export const SUBSCRIPTION_DISCOUNT_RATE = 0.3

// 게이트웨이가 /api/subscriptions/** 를 라우팅해주지 않아 (고정 라우팅: /api/{users,courses,enrollments,payments,recommend}/**),
// SubscriptionController를 payment-service 안에서 /api/payments/subscriptions 밑으로 옮겼다.
export const subscriptionApi = {
  // POST /api/payments/subscriptions  body: { designerId }
  subscribe(designerId) {
    return api.post('/api/payments/subscriptions', { designerId })
  },
  // DELETE /api/payments/subscriptions/{designerId}
  cancel(designerId) {
    return api.delete(`/api/payments/subscriptions/${designerId}`)
  },
  // GET /api/payments/subscriptions/my
  getMySubscriptions() {
    return api.get('/api/payments/subscriptions/my')
  }
}

/** 화면 표시용 추정 할인가. 실제 할인은 구매 시점에 서버가 계산한다. */
export function applyDiscount(price) {
  const value = Number(price ?? 0)
  if (Number.isNaN(value)) return 0
  return Math.round(value * (1 - SUBSCRIPTION_DISCOUNT_RATE))
}
