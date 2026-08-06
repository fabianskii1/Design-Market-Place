<template>
  <div class="page-wrapper">
    <AppHeader />
    <div class="page-layout">
      <!-- 사이드바 -->
      <aside class="sidebar">
        <div class="sidebar-section">
          <div class="sidebar-label">메뉴</div>

          <router-link
            to="/courses"
            class="sidebar-item"
            :class="{ active: $route.path === '/courses' }"
          >
            <span class="si-icon">📚</span> 디자인 목록
          </router-link>

          <router-link
            v-if="!isInstructor"
            to="/enrollments"
            class="sidebar-item"
          >
            <span class="si-icon">✅</span> 내 구매 목록
          </router-link>

          <router-link
            to="/mypage"
            class="sidebar-item"
          >
            <span class="si-icon">⭐</span> 마이페이지
          </router-link>
        </div>

        <div class="sidebar-section">
          <div class="sidebar-label">계정</div>
          <router-link to="/mypage" class="sidebar-item">
            <span class="si-icon">👤</span> 마이페이지
          </router-link>
          <button class="sidebar-item sidebar-btn" @click="handleLogout">
            <span class="si-icon">🚪</span> 로그아웃
          </button>
        </div>
      </aside>

      <!-- 메인 -->
      <main class="main-content">
        <div class="content-header">
          <div>
            <h1 class="page-title">디자인 목록</h1>
            <p class="page-subtitle" v-if="isInstructor">
              디자이너 계정으로 등록된 디자인을 확인하고 새 디자인을 추가할 수 있습니다.
            </p>
          </div>

          <router-link
            v-if="isInstructor"
            to="/courses/new"
            class="btn btn-primary create-course-btn"
          >
            디자인 등록
          </router-link>
        </div>

        <!-- 검색 · 정렬 -->
        <div class="search-bar">
          <div class="search-input-wrap">
            <span class="search-icon">🔍</span>
            <input
              v-model="keyword"
              type="search"
              class="search-input"
              placeholder="디자인명 또는 설명으로 검색"
              aria-label="디자인 검색"
            />
            <button
              v-if="keyword"
              type="button"
              class="search-clear"
              aria-label="검색어 지우기"
              @click="keyword = ''"
            >×</button>
          </div>

          <select v-model="sort" class="sort-select" aria-label="정렬 기준">
            <option v-for="opt in sortOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <!-- 필터 -->
        <div class="filter-bar">
          <button
            v-for="cat in categories"
            :key="cat"
            :class="['filter-chip', { active: selectedCategory === cat }]"
            @click="selectCategory(cat)"
          >
            {{ cat }}
          </button>
        </div>

        <p v-if="!loading" class="result-count">
          총 {{ filteredCourses.length }}개
          <span v-if="keyword" class="result-keyword">· '{{ keyword }}' 검색 결과</span>
        </p>

        <!-- 로딩 -->
        <div v-if="loading" class="loading-grid">
          <div v-for="i in 6" :key="i" class="skeleton-card">
            <div class="skeleton-thumb"></div>
            <div class="skeleton-body">
              <div class="skeleton-line short"></div>
              <div class="skeleton-line"></div>
              <div class="skeleton-line medium"></div>
            </div>
          </div>
        </div>

        <!-- 디자인 그리드 -->
        <div v-else-if="filteredCourses.length" class="course-grid fade-in">
          <CourseCard
            v-for="course in filteredCourses"
            :key="course.id"
            :course="course"
          />
        </div>

        <!-- 빈 상태 -->
        <div v-else class="empty-state">
          <p v-if="keyword">'{{ keyword }}'에 해당하는 디자인이 없습니다.</p>
          <p v-else>해당 카테고리의 디자인이 없습니다.</p>

          <router-link
            v-if="isInstructor"
            to="/courses/new"
            class="btn btn-primary empty-action-btn"
          >
            첫 디자인 등록하기
          </router-link>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import CourseCard from '@/components/CourseCard.vue'
import { useCourseStore } from '@/store/course.js'
import { useAuthStore } from '@/store/auth.js'

const router = useRouter()
const courseStore = useCourseStore()
const auth = useAuthStore()

const { categories, loading } = courseStore

const selectedCategory = computed(() => courseStore.selectedCategory)
const isInstructor = computed(() => auth.user?.role === 'INSTRUCTOR')

// 검색·정렬은 우선 프론트에서 처리한다.
// 백엔드에 GET /api/designs?keyword=&category=&sort= 가 열리면
// filteredCourses 대신 서버 응답을 그대로 쓰도록 바꾸면 된다.
const keyword = ref('')
const sort = ref('latest')

const sortOptions = [
  { value: 'latest',    label: '최신순' },
  { value: 'popular',   label: '인기순' },
  { value: 'priceAsc',  label: '가격 낮은순' },
  { value: 'priceDesc', label: '가격 높은순' },
  { value: 'title',     label: '이름순' }
]

const filteredCourses = computed(() => {
  if (!Array.isArray(courseStore.courses)) return []

  let list = courseStore.courses

  if (selectedCategory.value !== '전체') {
    list = list.filter(c => c.category === selectedCategory.value)
  }

  const q = keyword.value.trim().toLowerCase()
  if (q) {
    list = list.filter(c =>
      (c.title || '').toLowerCase().includes(q) ||
      (c.description || '').toLowerCase().includes(q)
    )
  }

  // computed가 원본 배열을 변형하지 않도록 복사 후 정렬
  return [...list].sort((a, b) => {
    switch (sort.value) {
      case 'popular':
        return (b.enrollmentCount ?? 0) - (a.enrollmentCount ?? 0)
      case 'priceAsc':
        return Number(a.price ?? 0) - Number(b.price ?? 0)
      case 'priceDesc':
        return Number(b.price ?? 0) - Number(a.price ?? 0)
      case 'title':
        return (a.title || '').localeCompare(b.title || '', 'ko')
      case 'latest':
      default:
        // createdAt이 없으면 id 역순으로 대체
        if (a.createdAt && b.createdAt) return b.createdAt.localeCompare(a.createdAt)
        return (b.id ?? 0) - (a.id ?? 0)
    }
  })
})

function selectCategory(cat) {
  courseStore.setCategory(cat)
}

function handleLogout() {
  auth.logout()
  router.push('/')
}

onMounted(() => {
  courseStore.fetchCourses()
})
</script>

<style scoped>
/* ── 검색 · 정렬 ───────────────────────────────── */
.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.search-input-wrap {
  position: relative;
  flex: 1;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 13px;
  opacity: 0.55;
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 9px 34px 9px 34px;
  border: 1px solid var(--color-border, #d7dbe3);
  border-radius: var(--radius-md, 10px);
  font-size: 14px;
  font-family: var(--font-sans, inherit);
  background: var(--color-bg-primary, #fff);
  color: var(--color-text-primary, #1e2430);
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary, #2d5bd7);
}

.search-input::-webkit-search-cancel-button {
  display: none;
}

.search-clear {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: none;
  font-size: 18px;
  line-height: 1;
  color: var(--color-text-muted, #8b93a3);
  cursor: pointer;
  padding: 2px 6px;
}

.sort-select {
  padding: 9px 12px;
  border: 1px solid var(--color-border, #d7dbe3);
  border-radius: var(--radius-md, 10px);
  font-size: 14px;
  font-family: var(--font-sans, inherit);
  background: var(--color-bg-primary, #fff);
  color: var(--color-text-primary, #1e2430);
  cursor: pointer;
}

.result-count {
  font-size: 13px;
  color: var(--color-text-muted, #8b93a3);
  margin: 0 0 14px;
}

.result-keyword {
  color: var(--color-text-secondary, #5b6475);
}

.page-wrapper {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}

.page-layout {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 28px;
}

/* 사이드바 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
}

.sidebar-label {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-text-muted);
  padding: 8px 12px 4px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--color-text-secondary);
  transition: var(--transition);
  background: none;
  border: none;
  width: 100%;
  text-align: left;
  cursor: pointer;
  font-family: var(--font-sans);
  text-decoration: none;
}

.sidebar-item:hover {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
}

.sidebar-item.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 500;
}

.si-icon {
  font-size: 15px;
}

.sidebar-btn {
  color: var(--color-text-secondary);
}

/* 메인 */
.main-content {
  min-width: 0;
}

.content-header {
  margin-bottom: 20px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.page-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.create-course-btn {
  white-space: nowrap;
  text-decoration: none;
}

/* 필터 */
.filter-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.filter-chip {
  padding: 7px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  border: 1.5px solid var(--color-border);
  background: var(--color-bg-primary);
  color: var(--color-text-secondary);
  transition: var(--transition);
  cursor: pointer;
}

.filter-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.filter-chip.active {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

/* 디자인 그리드 */
.course-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

/* 스켈레톤 */
.loading-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.skeleton-card {
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--color-border);
}

.skeleton-thumb {
  height: 120px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

.skeleton-body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

.skeleton-line.short {
  width: 40%;
}

.skeleton-line.medium {
  width: 70%;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

/* 빈 상태 */
.empty-state {
  text-align: center;
  padding: 80px 0;
  color: var(--color-text-muted);
  font-size: 15px;
}

.empty-action-btn {
  display: inline-flex;
  margin-top: 16px;
  text-decoration: none;
}

@media (max-width: 992px) {
  .page-layout {
    grid-template-columns: 1fr;
  }

  .course-grid,
  .loading-grid {
    grid-template-columns: 1fr;
  }

  .content-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>