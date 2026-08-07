import api from './index.js'

export const enrollmentApi = {
  getMyEnrollments() {
    return api.get('/api/enrollments/my')
  },
  /**
   * licenseTierId: license_tiers.id (숫자) — 백엔드 EnrollRequest.licenseTier가 Long 타입이라
   * 'PERSONAL' 같은 문자열이 아니라 실제 등급 레코드의 id를 보내야 한다.
   */
  enroll(courseId, licenseTierId) {
    return api.post('/api/enrollments', { courseId, licenseTier: licenseTierId })
  },
  cancel(enrollmentId) {
    return api.delete(`/api/enrollments/${enrollmentId}`)
  },
  getRecommendations(userId) {
    return api.get(`/api/recommend/${userId}`)
  }
}
