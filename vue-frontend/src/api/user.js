import api from './index.js'

export const userApi = {
  /**
   * 사용자 단건 조회. 디자이너 연락처 노출에 사용한다.
   *
   * course 응답에는 instructorId 만 있고 이름·이메일이 없어,
   * 상세 화면에서 이 ID로 한 번 더 조회한다.
   * 백엔드가 나중에 designerName / designerEmail 을 CourseResponse 에 포함시키면
   * 이 호출은 제거할 수 있다.
   */
  getById(userId) {
    return api.get(`/api/users/${userId}`)
  }
}
