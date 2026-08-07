import axios from 'axios'
import { useAuthStore } from '@/store/auth.js'

const api = axios.create({
  baseURL: '',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }

  // 파일 업로드(FormData)는 Content-Type을 지워야 한다.
  // 인스턴스 기본값(application/json)이 남으면 boundary가 붙지 않아
  // 서버가 파트를 파싱하지 못하고 400/500을 반환한다.
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    if (typeof config.headers.delete === 'function') {
      config.headers.delete('Content-Type')
    } else {
      delete config.headers['Content-Type']
    }
  }

  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      console.error('[API] 401 Unauthorized')
      console.error('[API] response data =', err.response?.data)
      console.error('[API] request url =', err.config?.url)
      // 디버깅 중에는 자동 로그아웃/리다이렉트 잠시 비활성화
      // const auth = useAuthStore()
      // auth.logout()
      // window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api