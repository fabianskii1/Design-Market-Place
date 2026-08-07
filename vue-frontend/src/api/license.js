export const LICENSE_TIERS = [
  {
    tier: 'PERSONAL',
    label: '개인용',
    summary: '비상업적 용도로만 사용, 제작물 재판매 불가',
    bullets: ['비상업적 용도로만 사용 가능', '제작물(2차 결과물) 재판매 불가']
  },
  {
    tier: 'COMMERCIAL_SMALL',
    label: '상업용 (소규모)',
    summary: '상업 이용 가능, 결과물 판매 수량 상한 있음',
    bullets: ['상업적 용도 사용 가능', '결과물 판매 수량 상한 있음 (예: N개 이하)']
  },
  {
    tier: 'COMMERCIAL_LARGE',
    label: '상업용 (대규모)',
    summary: '수량 무제한, 결과물 자체 굿즈 재판매 가능',
    bullets: ['판매 수량 무제한', '결과물 자체를 굿즈처럼 재판매 가능']
  }
]

// 3개 티어 공통 조항
export const COMMON_CLAUSE = '원본 디자인 파일 자체의 재배포·재판매는 모든 등급에서 금지됩니다.'