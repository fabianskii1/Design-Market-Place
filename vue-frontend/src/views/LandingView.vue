<template>
  <div class="landing">
    <AppHeader />

    <!-- 히어로 섹션 -->
    <section class="hero">
    <div class="hero-inner">
      <div class="hero-content fade-in-up">
        <span class="hero-badge">MSA 기반 디자인 마켓플레이스</span>
        <h1 class="hero-title">디자인을 사고팔다,<br>더 스마트하게</h1>
        <p class="hero-desc">로고, UI 키트, 일러스트까지 전문 디자이너의 작품을 만나보세요.</p>
        <div class="hero-actions">
         <router-link to="/login" class="btn btn-primary btn-lg">무료로 시작하기</router-link>
         <router-link to="/courses" class="btn btn-outline btn-lg">디자인 둘러보기</router-link>
        </div>
        <div class="hero-stats">
         <div class="stat"><span class="stat-num">1,200+</span><span class="stat-label">디자인</span></div>
         <div class="stat"><span class="stat-num">340+</span><span class="stat-label">디자이너</span></div>
         <div class="stat"><span class="stat-num">28,000+</span><span class="stat-label">구매자</span></div>
        </div>
      </div>
      <div class="hero-visual fade-in">
       <img src="@/assets/images/logo/main_logo.png" alt="DesignMarket" class="hero-logo" />
      </div>
    </div>
  </section>
    <!-- 인기 디자인 -->
    <section class="popular-section">
      <div class="section-inner">
        <div class="section-header">
          <h2 class="section-title">인기 디자인</h2>
          <router-link to="/login" class="section-link">전체 보기 →</router-link>
        </div>
        <div class="course-grid">
          <div v-for="course in featuredCourses" :key="course.id" class="course-card-landing">
            <div class="card-thumb" :class="course.thumbBg">
              <img :src="course.thumbSrc" :alt="course.title" class="thumb-img" />
            </div>
            <div class="card-body">
              <span class="badge" :class="course.badgeClass">{{ course.category }}</span>
              <h3 class="card-title">{{ course.title }}</h3>
              <div class="card-meta">
                <span class="instructor">{{ course.instructor }}</span>
                <span class="price">{{ course.price }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 특징 섹션 -->
    <section class="features-section">
      <div class="section-inner">
        <h2 class="section-title center">왜 DesignMarket인가요?</h2>
        <div class="features-grid">
          <div v-for="f in features" :key="f.title" class="feature-card">
            <div class="feature-icon">{{ f.icon }}</div>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta-section">
      <div class="cta-inner">
        <h2>지금 바로 시작하세요</h2>
        <p>수천 명의 디자이너와 구매자들이 DesignMarket과 함께 성장하고 있습니다.</p>
        <router-link to="/login" class="btn btn-primary btn-lg">무료로 시작하기</router-link>
      </div>
    </section>

    <!-- 푸터 -->
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-logo">
          <img src="@/assets/images/logo/main_logo.png" alt="DesignMarket" />
          <span>DesignMarket</span>
        </div>
        <p class="footer-copy">© 2026 DesignMarket. All rights reserved.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'

// TODO: 실제 디자인 썸네일 이미지 파일로 교체 필요 (assets/images/courses/ 하위)
import logoImg    from '@/assets/images/courses/spring_boot.png'   // → logo_branding.png 로 교체 예정
import uiKitImg   from '@/assets/images/courses/vue_js.png'        // → ui_ux_kit.png 로 교체 예정
import illustImg  from '@/assets/images/courses/kubernetes.png'    // → illustration.png 로 교체 예정
import iconImg    from '@/assets/images/courses/docker.png'        // → icon.png 로 교체 예정
import templateImg from '@/assets/images/courses/python.png'       // → template.png 로 교체 예정
import brandImg   from '@/assets/images/courses/generative_ai.png' // → 예비 카테고리용

const featuredCourses = [
  { id:1, title:'미니멀 로고 디자인 세트',      category:'로고 / 브랜딩', instructor:'김디자이너', price:'₩89,000', thumbSrc: logoImg,     thumbBg:'thumb-teal',   badgeClass:'badge-teal'   },
  { id:2, title:'모바일 앱 UI 키트',            category:'UI/UX 키트',   instructor:'이디자이너', price:'₩69,000', thumbSrc: uiKitImg,    thumbBg:'thumb-blue',   badgeClass:'badge-blue'   },
  { id:3, title:'플랫 스타일 일러스트 팩',       category:'일러스트',     instructor:'박디자이너', price:'₩99,000', thumbSrc: illustImg,   thumbBg:'thumb-purple', badgeClass:'badge-purple' },
  { id:4, title:'라인 아이콘 250종',            category:'아이콘',       instructor:'정디자이너', price:'₩79,000', thumbSrc: iconImg,     thumbBg:'thumb-pink',   badgeClass:'badge-pink'   },
  { id:5, title:'SNS 카드뉴스 템플릿',          category:'템플릿',       instructor:'최디자이너', price:'₩59,000', thumbSrc: templateImg, thumbBg:'thumb-teal',   badgeClass:'badge-teal'   },
  { id:6, title:'브랜드 아이덴티티 패키지',      category:'로고 / 브랜딩', instructor:'한디자이너', price:'₩75,000', thumbSrc: brandImg,    thumbBg:'thumb-blue',   badgeClass:'badge-blue'   },
]

const features = [
  { icon:'🚀', title:'전문 디자이너 작품',   desc:'검증된 디자이너들이 직접 제작한 고품질 디자인을 만나보세요.' },
  { icon:'🎯', title:'맞춤 디자인 추천',     desc:'AI 기반 추천 시스템이 구매 이력을 분석해 딱 맞는 디자인을 추천합니다.' },
  { icon:'💳', title:'간편한 구매 결제',     desc:'원클릭 결제와 즉시 다운로드로 바로 사용을 시작하세요.' },
  { icon:'📱', title:'언제 어디서나',        desc:'PC, 태블릿, 모바일 어디서든 끊김 없이 이용하세요.' },
]
</script>

<style scoped>
.landing { background: var(--color-bg-secondary); }

/* 히어로 */
.hero {
  background: linear-gradient(135deg, #f0f7ff 0%, #e8f4fd 50%, #f0f9ff 100%);
  border-bottom: 1px solid var(--color-border);
  padding: 80px 0 64px;
}
.hero-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 48px;
  align-items: center;
}
.hero-badge {
  display: inline-block;
  padding: 5px 14px;
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 16px;
}
.hero-title {
  font-size: 42px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.5px;
  color: var(--color-text-primary);
  margin-bottom: 16px;
}
.hero-desc {
  font-size: 16px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  max-width: 460px;
  margin-bottom: 28px;
}
.hero-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 40px;
}
.btn-lg { padding: 12px 28px; font-size: 15px; }
.hero-stats {
  display: flex;
  gap: 36px;
}
.stat { display: flex; flex-direction: column; gap: 2px; }
.stat-num { font-size: 22px; font-weight: 700; color: var(--color-primary); }
.stat-label { font-size: 12px; color: var(--color-text-secondary); }
.hero-visual {
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-logo {
  width: 200px;
  height: 200px;
  object-fit: contain;
  border-radius: 24px;
  box-shadow: var(--shadow-lg);
}

/* 디자인 섹션 */
.popular-section { padding: 64px 0; }
.section-inner { max-width: 1200px; margin: 0 auto; padding: 0 24px; }
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.section-title { font-size: 22px; font-weight: 700; color: var(--color-text-primary); }
.section-title.center { text-align: center; margin-bottom: 40px; }
.section-link { font-size: 14px; color: var(--color-primary); font-weight: 500; }
.section-link:hover { text-decoration: underline; }

.course-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.course-card-landing {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: var(--transition);
}
.course-card-landing:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}
.card-thumb {
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.thumb-teal   { background: #E1F5EE; }
.thumb-blue   { background: #E6F1FB; }
.thumb-purple { background: #EEEDFE; }
.thumb-pink   { background: #FBEAF0; }
.thumb-img { width: 100%; height: 100%; object-fit: contain; padding: 14px; }
.card-body { padding: 14px 16px; display: flex; flex-direction: column; gap: 6px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--color-text-primary); line-height: 1.4; }
.card-meta { display: flex; justify-content: space-between; align-items: center; }
.instructor { font-size: 12px; color: var(--color-text-secondary); }
.price { font-size: 14px; font-weight: 600; color: var(--color-primary); }

/* 특징 */
.features-section { padding: 64px 0; background: var(--color-bg-primary); }
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.feature-card {
  padding: 28px 24px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-align: center;
  transition: var(--transition);
}
.feature-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.feature-icon { font-size: 32px; margin-bottom: 12px; }
.feature-title { font-size: 15px; font-weight: 600; margin-bottom: 8px; }
.feature-desc { font-size: 13px; color: var(--color-text-secondary); line-height: 1.6; }

/* CTA */
.cta-section {
  padding: 80px 0;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  text-align: center;
}
.cta-inner { max-width: 600px; margin: 0 auto; padding: 0 24px; }
.cta-inner h2 { font-size: 32px; font-weight: 700; color: #fff; margin-bottom: 12px; }
.cta-inner p { font-size: 16px; color: rgba(255,255,255,0.8); margin-bottom: 32px; }
.cta-inner .btn-primary {
  background: #fff;
  color: var(--color-primary);
  border-color: #fff;
  font-weight: 600;
}
.cta-inner .btn-primary:hover { background: #f0f7ff; }

/* 푸터 */
.footer {
  background: var(--color-text-primary);
  padding: 32px 0;
}
.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.footer-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}
.footer-logo img { width: 28px; height: 28px; border-radius: 6px; }
.footer-copy { font-size: 13px; color: rgba(255,255,255,0.5); }
</style>
