<template>
  <div class="page-wrapper">
    <AppHeader />

    <div class="detail-layout" v-if="course">
      <div class="detail-hero">
        <div class="detail-hero-inner">
          <!-- 좌측 상세 정보 -->
          <div class="detail-info fade-in-up">
            <span class="badge" :class="badgeClass">{{ displayCategory }}</span>
            <h1 class="detail-title">{{ course.title }}</h1>
            <p class="detail-desc">
              {{ course.description || '실무 전문가가 직접 설계한 커리큘럼으로 체계적으로 학습하세요.' }}
            </p>

            <div class="detail-meta">
              <span>디자이너: {{ displayInstructorName }}</span>
            </div>
          </div>

          <!-- 우측 결제/수강 카드 -->
          <div class="enroll-card fade-in">
            <div class="enroll-thumb" :class="thumbBg">
              <div v-if="assetLoading" class="thumb-skeleton"></div>
              <img
                v-else-if="assetSrc"
                :src="assetSrc"
                :alt="course.title"
                class="asset-preview"
              />
              <img v-else-if="thumbSrc" :src="thumbSrc" :alt="course.title" />
            </div>
            <p v-if="assetSrc" class="watermark-note">워터마크가 적용된 미리보기입니다.</p>

            <div class="enroll-body">
              <div class="enroll-price">₩{{ displayPrice }}</div>

              <button
                class="btn btn-primary btn-full"
                @click="handlePrimaryAction"
                :disabled="buttonDisabled"
                :class="{ 'btn-disabled': buttonDisabled }"
              >
                <span v-if="enrolling">처리 중...</span>
                <span v-else>{{ buttonLabel }}</span>
              </button>

              <div v-if="enrollError" class="error-msg">{{ enrollError }}</div>

              <p class="helper-text" v-if="helperText">
                {{ helperText }}
              </p>

              <!-- 구매자·소유자 다운로드 -->
              <button
                v-if="canDownload"
                type="button"
                class="btn btn-outline btn-full asset-btn"
                :disabled="downloading"
                @click="handleDownload"
              >
                <span v-if="downloading">내려받는 중...</span>
                <span v-else>원본 다운로드</span>
              </button>

              <!-- 소유자 파일 업로드 / 교체 -->
              <div v-if="isOwner" class="owner-upload">
                <button
                  type="button"
                  class="btn btn-ghost btn-full asset-btn"
                  :disabled="uploading"
                  @click="openFilePicker"
                >
                  <span v-if="uploading">업로드 중 {{ uploadProgress }}%</span>
                  <span v-else>{{ assetSrc ? '파일 교체' : '디자인 파일 업로드' }}</span>
                </button>

                <input
                  ref="fileInput"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  class="file-input-hidden"
                  @change="handleFileChange"
                />

                <div v-if="uploading" class="progress">
                  <div class="progress-bar" :style="{ width: uploadProgress + '%' }"></div>
                </div>
              </div>

              <p v-if="assetMessage" class="asset-msg" :class="{ 'is-error': assetIsError }">
                {{ assetMessage }}
              </p>

              <ul class="enroll-info-list">
                <li>✅ 즉시 다운로드</li>
                <li>✅ 영구 이용</li>
                <li>✅ 라이선스 증빙 제공</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="loading" class="loading-center">
      <div class="spinner"></div>
    </div>

    <div v-else class="loading-center">
      <p class="empty-text">디자인 정보를 불러오지 못했습니다.</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { useCourseStore } from '@/store/course.js'
import { enrollmentApi } from '@/api/enrollment.js'
import { courseApi } from '@/api/course.js'
import { useAssetImage, invalidateAssetCache } from '@/composables/useAssetImage.js'
import { useAuthStore } from '@/store/auth.js'

const route = useRoute()
const router = useRouter()
const courseStore = useCourseStore()
const auth = useAuthStore()

const enrolling = ref(false)
const enrollError = ref('')

// ── 디자인 자산 (미리보기 · 업로드 · 다운로드) ──────────────
const fileInput = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const downloading = ref(false)
const assetMessage = ref('')
const assetIsError = ref(false)
const enrollmentStatus = ref('NONE') // NONE | PENDING | ACTIVE

const course = computed(() => courseStore.selectedCourse)
const loading = computed(() => courseStore.loading)
const isInstructor = computed(() => auth.user?.role === 'INSTRUCTOR')

const categoryConfig = {
  '로고 / 브랜딩':    { bg: 'thumb-teal',   badge: 'badge-teal',   thumb: 'spring_boot' },
  'UX / UI 키트':{ bg: 'thumb-teal',   badge: 'badge-teal',   thumb: 'vue_js' },
  '일러스트':   { bg: 'thumb-blue',   badge: 'badge-blue',   thumb: 'docker' },
  '아이콘':   { bg: 'thumb-purple', badge: 'badge-purple', thumb: 'python' },
  '템플릿':       { bg: 'thumb-pink',   badge: 'badge-pink',   thumb: 'generative_ai' },
}

const config = computed(() => categoryConfig[course.value?.category] || {})
const badgeClass = computed(() => config.value.badge || 'badge-gray')
const thumbBg = computed(() => config.value.bg || 'thumb-gray')

const displayCategory = computed(() => course.value?.category || '-')

const displayInstructorName = computed(() => {
  return (
    course.value?.instructorName ||
    course.value?.teacherName ||
    course.value?.instructor?.name ||
    course.value?.instructor_name ||
    course.value?.ownerName ||
    '디자이너 정보 없음'
  )
})

const displayEnrollmentCount = computed(() => {
  const value = Number(
    course.value?.enrollmentCount ??
    course.value?.enrollment_count ??
    0
  )
  return Number.isNaN(value) ? 0 : value.toLocaleString()
})

const displayPrice = computed(() => {
  const value = Number(course.value?.price ?? 0)
  return Number.isNaN(value) ? '0' : value.toLocaleString()
})

// 미리보기는 인증이 필요해 blob으로 받아온다 (CourseCard와 동일한 방식)
const { src: assetSrc, loading: assetLoading } = useAssetImage(
  computed(() => course.value?.id),
  computed(() => course.value?.hasAsset ?? false)
)

const isOwner = computed(() => {
  const ownerId = course.value?.instructorId ?? course.value?.instructor_id
  return !!ownerId && Number(ownerId) === Number(auth.user?.id)
})

// 소유자이거나 구매가 완료된 사용자만 원본을 받을 수 있다
const canDownload = computed(() =>
  !!assetSrc.value && (isOwner.value || enrollmentStatus.value === 'ACTIVE')
)

function openFilePicker() {
  fileInput.value?.click()
}

async function handleFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  assetMessage.value = ''
  assetIsError.value = false

  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    assetIsError.value = true
    assetMessage.value = 'PNG, JPG, WEBP 형식만 업로드할 수 있습니다.'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    assetIsError.value = true
    assetMessage.value = '파일 크기는 10MB를 넘을 수 없습니다.'
    return
  }

  uploading.value = true
  uploadProgress.value = 0

  try {
    await courseApi.uploadAsset(course.value.id, file, (percent) => {
      uploadProgress.value = percent
    })
    assetMessage.value = '파일이 업로드되었습니다.'
    // 캐시된 이전 미리보기를 버리고 갱신된 course(hasAsset)를 다시 받아온다
    invalidateAssetCache(course.value.id)
    await courseStore.fetchCourse(course.value.id)
  } catch (e) {
    console.error('[CourseDetail] asset upload failed:', e)
    assetIsError.value = true
    const status = e.response?.status
    assetMessage.value = (status === 404 || status === 405)
      ? '파일 업로드 API가 아직 준비되지 않았습니다.'
      : (e.response?.data?.message || '파일 업로드에 실패했습니다.')
  } finally {
    uploading.value = false
  }
}

async function handleDownload() {
  assetMessage.value = ''
  assetIsError.value = false
  downloading.value = true

  try {
    const res = await courseApi.downloadAsset(course.value.id)

    // Content-Disposition에 담긴 파일명을 우선 사용한다
    const disposition = res.headers?.['content-disposition'] || ''
    const match = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
    const filename = match
      ? decodeURIComponent(match[1])
      : `design_${course.value.id}.png`

    const url = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error('[CourseDetail] asset download failed:', e)
    assetIsError.value = true
    const status = e.response?.status
    assetMessage.value = status === 403
      ? '구매한 사용자만 원본을 내려받을 수 있습니다.'
      : (status === 404 || status === 405)
        ? '다운로드 API가 아직 준비되지 않았습니다.'
        : '다운로드에 실패했습니다.'
  } finally {
    downloading.value = false
  }
}

const thumbSrc = computed(() => {
  const key = course.value?.thumbnail || config.value.thumb
  if (!key) return null

  try {
    return new URL(`../assets/images/courses/${key}.png`, import.meta.url).href
  } catch {
    return null
  }
})

const buttonLabel = computed(() => {
  if (isInstructor.value) return '디자이너 계정은 신청 불가'
  if (enrollmentStatus.value === 'ACTIVE') return '내 수강 목록으로 이동'
  if (enrollmentStatus.value === 'PENDING') return '신청 완료 · 결제 처리 중'
  return '결제하고 수강하기'
})

const buttonDisabled = computed(() => {
  if (enrolling.value) return true
  if (isInstructor.value) return true
  if (enrollmentStatus.value === 'PENDING') return true
  return false
})

const helperText = computed(() => {
  if (isInstructor.value) {
    return '디자이너 계정은 본인 디자인을 수강 신청할 수 없습니다.'
  }

  if (enrollmentStatus.value === 'ACTIVE') {
    return '이미 수강 중인 디자인입니다. 내 수강 목록에서 바로 이어서 학습할 수 있습니다.'
  }

  if (enrollmentStatus.value === 'PENDING') {
    return '수강 신청이 접수되었습니다. 결제/처리 상태가 반영되면 내 수강 목록에서 확인할 수 있습니다.'
  }

  return '결제를 진행하면 수강 신청이 함께 처리됩니다.'
})

async function loadEnrollmentStatus() {
  if (!auth.user?.id || !course.value?.id || isInstructor.value) {
    enrollmentStatus.value = 'NONE'
    return
  }

  try {
    const res = await enrollmentApi.getMyEnrollments()
    console.log('[CourseDetail] my enrollments response =', res.data)

    const enrollments = Array.isArray(res.data?.data)
      ? res.data.data
      : Array.isArray(res.data)
        ? res.data
        : []

    const matched = enrollments.find(item => Number(item.courseId) === Number(course.value.id))

    if (!matched) {
      enrollmentStatus.value = 'NONE'
      return
    }

    enrollmentStatus.value = matched.status === 'ACTIVE' ? 'ACTIVE' : 'PENDING'
  } catch (e) {
    console.error('[CourseDetail] failed to load enrollment status:', e)
    enrollmentStatus.value = 'NONE'
  }
}

async function handlePrimaryAction() {
  enrollError.value = ''

  if (!course.value?.id) {
    enrollError.value = '디자인 정보가 올바르지 않습니다.'
    return
  }

  if (isInstructor.value) {
    enrollError.value = '디자이너 계정은 본인 디자인을 수강 신청할 수 없습니다.'
    return
  }

  if (enrollmentStatus.value === 'ACTIVE') {
    router.push('/enrollments')
    return
  }

  if (enrollmentStatus.value === 'PENDING') {
    return
  }

  enrolling.value = true

  try {
    await enrollmentApi.enroll(course.value.id)
    enrollmentStatus.value = 'PENDING'
  } catch (e) {
    console.error('[CourseDetail] enroll failed:', e)
    enrollError.value = e.response?.data?.message || '결제/수강 신청에 실패했습니다.'
  } finally {
    enrolling.value = false
  }
}

onMounted(async () => {
  await courseStore.fetchCourse(route.params.id)
  console.log('[CourseDetail] selectedCourse =', courseStore.selectedCourse)
  await loadEnrollmentStatus()
})

watch(
  () => courseStore.selectedCourse,
  async (value) => {
    console.log('[CourseDetail] selectedCourse changed =', value)
    if (value?.id) {
      assetMessage.value = ''
      await loadEnrollmentStatus()
    }
  },
  { deep: true }
)
</script>

<style scoped>
/* ── 디자인 자산 ───────────────────────────────── */
.asset-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-skeleton {
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, #eef1f6 25%, #f6f8fb 50%, #eef1f6 75%);
  background-size: 200% 100%;
  animation: thumb-shimmer 1.2s infinite;
}

@keyframes thumb-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.watermark-note {
  font-size: 11px;
  color: var(--color-text-muted, #8b93a3);
  text-align: center;
  margin: 6px 0 0;
}

.asset-btn {
  margin-top: 8px;
}

.file-input-hidden {
  display: none;
}

.progress {
  height: 5px;
  border-radius: 3px;
  background: var(--color-bg-tertiary, #eef2fb);
  margin-top: 8px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: var(--color-primary, #2d5bd7);
  transition: width 0.2s ease;
}

.asset-msg {
  font-size: 12px;
  color: var(--color-text-secondary, #5b6475);
  margin: 10px 0 0;
}

.asset-msg.is-error {
  color: var(--color-danger, #d64545);
}

.page-wrapper {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}

.detail-hero {
  background: linear-gradient(135deg, #f0f7ff 0%, #e8f4fd 100%);
  border-bottom: 1px solid var(--color-border);
  padding: 48px 0;
}

.detail-hero-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 48px;
  align-items: start;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-title {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.3;
}

.detail-desc {
  font-size: 15px;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.detail-meta {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: var(--color-text-secondary);
  flex-wrap: wrap;
}

.enroll-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-md);
}

.enroll-thumb {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.enroll-thumb img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 20px;
}

.thumb-teal { background: #E1F5EE; }
.thumb-blue { background: #E6F1FB; }
.thumb-purple { background: #EEEDFE; }
.thumb-pink { background: #FBEAF0; }
.thumb-gray { background: #F1EFE8; }

.enroll-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.enroll-price {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-primary);
}

.btn-full {
  width: 100%;
  padding: 13px;
  font-size: 15px;
  justify-content: center;
}

.btn-disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.enroll-info-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.enroll-info-list li {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.error-msg {
  font-size: 13px;
  color: #dc2626;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: var(--radius-sm);
}

.helper-text {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.empty-text {
  font-size: 14px;
  color: var(--color-text-muted);
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 100px 0;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.badge-gray {
  background: #f3f4f6;
  color: #6b7280;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .detail-hero-inner {
    grid-template-columns: 1fr;
  }
}
</style>