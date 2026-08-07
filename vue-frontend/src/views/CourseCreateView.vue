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
            to="/courses/new"
            class="sidebar-item"
            :class="{ active: $route.path === '/courses/new' }"
          >
            <span class="si-icon">✍️</span> 디자인 등록
          </router-link>

          <router-link to="/mypage" class="sidebar-item">
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
            <h1 class="page-title">디자인 등록</h1>
            <p class="page-subtitle">디자이너 계정으로 새로운 디자인을 등록합니다.</p>
          </div>
        </div>

        <div class="form-card">
          <form class="course-form" @submit.prevent="handleSubmit">
            <!-- 디자인 파일 업로드 -->
            <div class="form-group">
              <label class="form-label">디자인 파일</label>

              <div
                v-if="!previewUrl"
                class="dropzone"
                :class="{ 'is-dragover': isDragOver }"
                role="button"
                tabindex="0"
                @click="openFilePicker"
                @keydown.enter.prevent="openFilePicker"
                @keydown.space.prevent="openFilePicker"
                @dragover.prevent="isDragOver = true"
                @dragleave.prevent="isDragOver = false"
                @drop.prevent="handleDrop"
              >
                <div class="dz-icon">🖼️</div>
                <p class="dz-title">파일을 끌어다 놓거나 클릭해서 선택하세요</p>
                <p class="dz-hint">PNG · JPG · WEBP / 최대 10MB / 권장 1200px 이상</p>
              </div>

              <div v-else class="preview-card">
                <div class="preview-thumb">
                  <img :src="previewUrl" alt="선택한 디자인 미리보기" />
                </div>

                <div class="preview-meta">
                  <p class="pm-name" :title="selectedFile?.name">{{ selectedFile?.name }}</p>
                  <p class="pm-sub">
                    {{ formattedSize }}
                    <span v-if="imageSize"> · {{ imageSize.width }} × {{ imageSize.height }}px</span>
                  </p>

                  <div v-if="uploadProgress > 0" class="progress">
                    <div class="progress-bar" :style="{ width: uploadProgress + '%' }"></div>
                    <span class="progress-label">{{ uploadProgress }}%</span>
                  </div>

                  <div class="preview-actions">
                    <button type="button" class="btn-link" @click="openFilePicker">변경</button>
                    <button type="button" class="btn-link danger" @click="clearFile">제거</button>
                  </div>
                </div>
              </div>

              <input
                ref="fileInput"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                class="file-input-hidden"
                @change="handleFileChange"
              />

              <p v-if="fileError" class="field-error">{{ fileError }}</p>
              <p class="field-hint">
                업로드한 원본에는 추적용 워터마크가 삽입되며, 목록에는 워터마크 미리보기가 표시됩니다.
              </p>
            </div>

            <div class="form-group">
              <label class="form-label" for="title">디자인명</label>
              <input
                id="title"
                v-model.trim="form.title"
                type="text"
                class="form-input"
                placeholder="예: 미니멀 로고 세트"
                maxlength="100"
              />
            </div>

            <div class="form-group">
              <label class="form-label" for="description">디자인 설명</label>
              <textarea
                id="description"
                v-model.trim="form.description"
                class="form-textarea"
                rows="6"
                placeholder="디자인 소개, 디자인 특징 등을 입력해 주세요."
              ></textarea>
            </div>

            <div class="form-section">
              <h3 class="form-section-title">카테고리 및 라이선스</h3>

              <div class="form-group">
                <label class="form-label" for="category">카테고리</label>
                <select id="category" v-model="form.category" class="form-select">
                  <option disabled value="">카테고리를 선택하세요</option>
                  <option
                    v-for="option in categoryOptions"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label">라이선스별 가격</label>
                <p class="field-hint">
                  등급별로 구매자가 어디까지 쓸 수 있는지 확인하고 가격을 정해주세요.
                </p>

                <div class="license-price-grid">
                  <div v-for="t in LICENSE_TIERS" :key="t.tier" class="license-price-item">
                    <div class="license-price-top">
                      <span class="license-price-label">{{ t.label }}</span>
                    </div>
                    <p class="license-price-summary">{{ t.summary }}</p>
                    <input
                      type="number"
                      min="0"
                      step="1000"
                      class="form-input"
                      placeholder="예: 50000"
                      v-model.number="licensePrices[t.tier]"
                    />
                  </div>
                </div>
                <p class="field-hint" v-if="derivedPrice">
                  목록에는 라이선스 중 가장 낮은 가격(₩{{ derivedPrice.toLocaleString() }})이 대표 가격으로 표시됩니다.
                </p>
              </div>
            </div>

            <div v-if="validationError" class="error-box">
              {{ validationError }}
            </div>

            <div v-if="submitError" class="error-box">
              {{ submitError }}
            </div>

            <div v-if="submitSuccess" class="success-box">
              {{ submitSuccess }}
            </div>

            <div class="form-actions">
              <router-link to="/courses" class="btn btn-ghost">
                취소
              </router-link>

              <button type="submit" class="btn btn-primary" :disabled="submitting">
                <span v-if="submitting">등록 중...</span>
                <span v-else>디자인 등록</span>
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { courseApi } from '@/api/course.js'
import { useAuthStore } from '@/store/auth.js'
import { LICENSE_TIERS } from '@/api/license.js'
import { CATEGORY_OPTIONS } from '@/api/category.js'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({
  title: '',
  description: '',
  category: ''
})
const licensePrices = reactive({
  PERSONAL: null,
  COMMERCIAL_SMALL: null,
  COMMERCIAL_LARGE: null
})

const derivedPrice = computed(() => {
  const values = Object.values(licensePrices)
    .map(Number)
    .filter(v => !Number.isNaN(v) && v > 0)
  return values.length ? Math.min(...values) : 0
})

const submitting = ref(false)
const validationError = ref('')

// ── 파일 업로드 상태 ──────────────────────────────
const MAX_FILE_BYTES = 10 * 1024 * 1024
const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp']
// LSB 워터마크를 심으려면 최소한의 픽셀 수가 필요하다
const MIN_EDGE = 400

const fileInput = ref(null)
const selectedFile = ref(null)
const previewUrl = ref('')
const imageSize = ref(null)
const fileError = ref('')
const isDragOver = ref(false)
const uploadProgress = ref(0)

const formattedSize = computed(() => {
  const bytes = selectedFile.value?.size ?? 0
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
})

function openFilePicker() {
  fileInput.value?.click()
}

function handleDrop(event) {
  isDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) selectFile(file)
}

function handleFileChange(event) {
  const file = event.target.files?.[0]
  if (file) selectFile(file)
  // 같은 파일을 다시 골라도 change가 발생하도록 초기화
  event.target.value = ''
}

function selectFile(file) {
  fileError.value = ''

  if (!ALLOWED_TYPES.includes(file.type)) {
    fileError.value = 'PNG, JPG, WEBP 형식만 업로드할 수 있습니다.'
    return
  }
  if (file.size > MAX_FILE_BYTES) {
    fileError.value = `파일 크기는 10MB를 넘을 수 없습니다. (현재 ${(file.size / 1024 / 1024).toFixed(1)}MB)`
    return
  }

  revokePreview()
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  uploadProgress.value = 0
  imageSize.value = null

  // 워터마크 삽입 가능 여부를 미리 확인해 등록 후 실패하는 상황을 막는다
  const probe = new Image()
  probe.onload = () => {
    imageSize.value = { width: probe.naturalWidth, height: probe.naturalHeight }
    if (Math.min(probe.naturalWidth, probe.naturalHeight) < MIN_EDGE) {
      fileError.value = `이미지가 너무 작습니다. 짧은 변이 ${MIN_EDGE}px 이상이어야 워터마크를 삽입할 수 있습니다.`
    }
  }
  probe.src = previewUrl.value
}

function clearFile() {
  revokePreview()
  selectedFile.value = null
  imageSize.value = null
  fileError.value = ''
  uploadProgress.value = 0
}

function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

onBeforeUnmount(revokePreview)

const submitError = ref('')
const submitSuccess = ref('')

const categoryOptions = CATEGORY_OPTIONS

function handleLogout() {
  auth.logout()
  router.push('/')
}

function validateForm() {
  validationError.value = ''

  if (!auth.user || auth.user.role !== 'INSTRUCTOR') {
    validationError.value = '디자이너 계정만 디자인을 등록할 수 있습니다.'
    return false
  }

  if (!selectedFile.value) {
    validationError.value = '디자인 파일을 선택해 주세요.'
    return false
  }

  if (fileError.value) {
    validationError.value = fileError.value
    return false
  }

  if (!form.title) {
    validationError.value = '디자인명을 입력해 주세요.'
    return false
  }

  if (!form.description) {
    validationError.value = '디자인 설명을 입력해 주세요.'
    return false
  }

  if (!form.category) {
    validationError.value = '카테고리를 선택해 주세요.'
    return false
  }

  const tierValues = Object.values(licensePrices)

  if (tierValues.some(v => v === null || v === undefined || v === '')) {
    validationError.value = '라이선스별 가격을 모두 입력해 주세요.'
    return false
  }

  if (tierValues.some(v => Number.isNaN(Number(v)) || Number(v) < 0)) {
    validationError.value = '라이선스별 가격은 0 이상의 숫자로 입력해 주세요.'
    return false
  }

  return true
}

async function handleSubmit() {
  submitError.value = ''
  submitSuccess.value = ''

  if (!validateForm()) return

  submitting.value = true

  try {
    const payload = {
      title: form.title,
      description: form.description,
      category: form.category,
      price: derivedPrice.value
    }

    const res = await courseApi.create(payload)
    console.log('[CourseCreate] create response =', res.data)

    const createdCourseId =
      res.data?.data?.id ??
      res.data?.id

    if (!createdCourseId) {
      submitSuccess.value = '디자인이 등록되었습니다.'
      setTimeout(() => router.push('/courses'), 500)
      return
    }

    // 디자인 등록이 성공한 뒤에야 파일을 올린다.
    // 업로드가 실패해도 등록 자체는 유지되므로, 상세 화면에서 재시도할 수 있다.
    submitSuccess.value = '디자인이 등록되었습니다. 파일을 업로드하는 중입니다...'

    try {
      await courseApi.uploadAsset(createdCourseId, selectedFile.value, (percent) => {
        uploadProgress.value = percent
      })
      submitSuccess.value = '디자인과 파일이 모두 등록되었습니다.'
    } catch (uploadError) {
      console.error('[CourseCreate] asset upload failed:', uploadError)
      const status = uploadError.response?.status

      // 백엔드 업로드 API가 아직 배포되지 않은 경우를 구분해 안내한다
      submitError.value = (status === 404 || status === 405)
        ? '디자인은 등록되었으나 파일 업로드 API가 아직 준비되지 않았습니다. 백엔드 배포 후 상세 화면에서 다시 올려주세요.'
        : (uploadError.response?.data?.message || '디자인은 등록되었으나 파일 업로드에 실패했습니다.')
    }
    try {
      const tiers = LICENSE_TIERS.map(t => ({
        tier: t.tier,
        price: Number(licensePrices[t.tier]) || 0
      }))
      await courseApi.createLicenseTiers(createdCourseId, tiers)
    } catch (tierError) {
      console.error('[CourseCreate] license tier save failed:', tierError)
      const status = tierError.response?.status
      submitError.value = (status === 404 || status === 405)
        ? '디자인은 등록되었으나 라이선스별 가격 저장 API가 아직 준비되지 않았습니다. 백엔드 배포 후 다시 설정해주세요.'
        : (tierError.response?.data?.message || '라이선스별 가격 저장에 실패했습니다.')
    }

    setTimeout(() => router.push(`/courses/${createdCourseId}`), 900)
  } catch (error) {
    console.error('[CourseCreate] create failed:', error)
    submitError.value =
      error.response?.data?.message ||
      '디자인 등록에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* ── 파일 업로드 ───────────────────────────────── */
.file-input-hidden {
  display: none;
}

.dropzone {
  border: 2px dashed var(--color-border, #d7dbe3);
  border-radius: var(--radius-md, 10px);
  padding: 36px 20px;
  text-align: center;
  cursor: pointer;
  transition: var(--transition, all 0.15s ease);
  background: var(--color-bg-secondary, #f7f8fa);
}

.dropzone:hover,
.dropzone:focus-visible,
.dropzone.is-dragover {
  border-color: var(--color-primary, #2d5bd7);
  background: var(--color-bg-tertiary, #eef2fb);
  outline: none;
}

.dz-icon {
  font-size: 30px;
  margin-bottom: 8px;
}

.dz-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary, #1e2430);
  margin: 0 0 4px;
}

.dz-hint {
  font-size: 12px;
  color: var(--color-text-muted, #8b93a3);
  margin: 0;
}

.preview-card {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--color-border, #d7dbe3);
  border-radius: var(--radius-md, 10px);
  background: #fff;
}

.preview-thumb {
  width: 116px;
  height: 116px;
  flex-shrink: 0;
  border-radius: var(--radius-sm, 6px);
  overflow: hidden;
  background: var(--color-bg-tertiary, #eef2fb);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.preview-meta {
  min-width: 0;
  flex: 1;
}

.pm-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary, #1e2430);
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pm-sub {
  font-size: 12px;
  color: var(--color-text-muted, #8b93a3);
  margin: 0 0 10px;
}

.progress {
  position: relative;
  height: 6px;
  border-radius: 3px;
  background: var(--color-bg-tertiary, #eef2fb);
  margin-bottom: 10px;
}

.progress-bar {
  height: 100%;
  border-radius: 3px;
  background: var(--color-primary, #2d5bd7);
  transition: width 0.2s ease;
}

.progress-label {
  position: absolute;
  right: 0;
  top: 8px;
  font-size: 11px;
  color: var(--color-text-muted, #8b93a3);
}

.preview-actions {
  display: flex;
  gap: 12px;
}

.btn-link {
  background: none;
  border: none;
  padding: 0;
  font-size: 13px;
  font-family: var(--font-sans, inherit);
  color: var(--color-primary, #2d5bd7);
  cursor: pointer;
}

.btn-link:hover {
  text-decoration: underline;
}

.btn-link.danger {
  color: var(--color-danger, #d64545);
}

.field-error {
  font-size: 12px;
  color: var(--color-danger, #d64545);
  margin: 8px 0 0;
}

.field-hint {
  font-size: 12px;
  color: var(--color-text-muted, #8b93a3);
  margin: 8px 0 0;
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

.form-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
}

.course-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-section {
  border: 1px solid var(--color-border, #d7dbe3);
  border-radius: var(--radius-md, 10px);
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary, #1e2430);
}

.license-price-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.license-price-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--color-border, #d7dbe3);
  border-radius: var(--radius-md, 10px);
  background: var(--color-bg-secondary, #f7f8fa);
}

.license-price-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.license-price-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary, #1e2430);
}

.license-price-summary {
  font-size: 12px;
  color: var(--color-text-secondary, #5b6475);
  line-height: 1.4;
  margin: 0;
}

@media (max-width: 640px) {
  .license-price-grid {
    grid-template-columns: 1fr;
  }
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.form-input,
.form-textarea,
.form-select {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  padding: 12px 14px;
  font-size: 14px;
  font-family: inherit;
  color: var(--color-text-primary);
  outline: none;
  transition: var(--transition);
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus,
.form-select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
}

.form-textarea {
  resize: vertical;
  min-height: 140px;
  line-height: 1.5;
}

.error-box {
  background: #fef2f2;
  color: #dc2626;
  border-radius: var(--radius-md);
  padding: 12px 14px;
  font-size: 13px;
}

.success-box {
  background: #ecfdf3;
  color: #15803d;
  border-radius: var(--radius-md);
  padding: 12px 14px;
  font-size: 13px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 6px;
}

@media (max-width: 992px) {
  .page-layout {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>