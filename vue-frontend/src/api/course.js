import api from './index.js'

export const courseApi = {
  getCourses(params) {
    return api.get('/api/courses', { params })
  },

  getAll(params) {
    return api.get('/api/courses', { params })
  },

  getById(id) {
    return api.get(`/api/courses/${id}`)
  },

  create(data) {
    return api.post('/api/courses', data)
  },

  update(id, data) {
    return api.put(`/api/courses/${id}`, data)
  },

  // ── 디자인 자산 (이미지) ────────────────────────────────
  // 백엔드 계약: POST /api/designs/{id}/asset (multipart, 필드명 file)
  // Gateway 라우팅이 /api/courses/** 로 고정되어 있어 현재는 courses 경로로 호출한다.
  // 백엔드에서 /api/designs 라우트가 열리면 아래 상수만 바꾸면 된다.
  uploadAsset(designId, file, onProgress) {
    const formData = new FormData()
    formData.append('file', file)

    return api.post(`/api/courses/${designId}/asset`, formData, {
      // FormData 전송 시 Content-Type을 비워야 axios가 boundary를 자동으로 붙인다.
      // 인스턴스 기본값(application/json)이 남으면 서버가 본문을 파싱하지 못한다.
      headers: { 'Content-Type': undefined },
      timeout: 120000,
      onUploadProgress(event) {
        if (!onProgress || !event.total) return
        onProgress(Math.round((event.loaded * 100) / event.total))
      }
    })
  },

  /** 워터마크 미리보기 이미지 URL (인증 불필요 — img src에 그대로 사용) */
  previewUrl(designId) {
    return `/api/courses/${designId}/asset/preview`
  },

  /** 워터마크본 다운로드 (인증 필요 — blob으로 받아 사용) */
  downloadAsset(designId) {
    return api.get(`/api/courses/${designId}/asset/download`, {
      responseType: 'blob',
      timeout: 120000
    })
  }
}
