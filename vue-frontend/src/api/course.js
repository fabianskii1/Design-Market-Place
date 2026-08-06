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

  uploadAsset(designId, file, onProgress) {
    const formData = new FormData()
    formData.append('file', file)

    return api.post(`/api/courses/${designId}/asset`, formData, {
      // FormData 전송 시 Content-Type을 비워야 axios가 boundary를 자동으로 붙인다.
      headers: { 'Content-Type': undefined },
      timeout: 120000,
      onUploadProgress(event) {
        if (!onProgress || !event.total) return
        onProgress(Math.round((event.loaded * 100) / event.total))
      }
    })
  },

  /**
   * 미리보기 이미지를 blob 으로 받아온다.
   *
   * img 태그의 src 로 직접 URL을 넣으면 브라우저가 요청을 보내면서
   * Authorization 헤더를 붙이지 않아 게이트웨이에서 401이 난다.
   * axios 로 받아 objectURL 을 만들어 쓰면 인터셉터가 토큰을 붙여준다.
   */
  fetchPreview(designId) {
    return api.get(`/api/courses/${designId}/asset/preview`, {
      responseType: 'blob',
      timeout: 30000
    })
  },

  /** 워터마크본 다운로드 */
  downloadAsset(designId) {
    return api.get(`/api/courses/${designId}/asset/download`, {
      responseType: 'blob',
      timeout: 120000
    })
  }
}