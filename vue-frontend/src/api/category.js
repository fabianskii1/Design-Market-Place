// 백엔드 Course.Category enum: BACKEND, FRONTEND, DEVOPS, DATA_SCIENCE, MOBILE, SECURITY, DATABASE, OTHER
// 카테고리 이름은 여기 한 곳만 고치면 등록 화면·목록·마이페이지에 전부 반영됩니다.
export const CATEGORY_OPTIONS = [
  { value: 'BACKEND', label: '로고 / 브랜딩' },
  { value: 'FRONTEND', label: 'UX / UI 키트' },
  { value: 'DEVOPS', label: '일러스트' },
  { value: 'MOBILE', label: '아이콘' },
  { value: 'DATA_SCIENCE', label: '템플릿' }
]

export function categoryLabel(value) {
  return CATEGORY_OPTIONS.find(c => c.value === value)?.label ?? value
}