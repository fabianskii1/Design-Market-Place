<template>
  <router-link :to="`/courses/${course.id}`" class="course-card">
    <!-- 썸네일 -->
    <div class="card-thumb" :class="thumbBg">
      <!--
        1순위: 업로드된 워터마크 미리보기
        2순위: 카테고리 기본 이미지 (자산 미등록이거나 API 미준비 시 onerror로 전환)
        3순위: 카테고리 첫 글자 플레이스홀더
      -->
      <img
        v-if="!assetFailed"
        :src="assetPreviewSrc"
        :alt="course.title"
        class="thumb-img thumb-cover"
        loading="lazy"
        @error="assetFailed = true"
      />
      <img v-else-if="thumbSrc" :src="thumbSrc" :alt="course.title" class="thumb-img" />
      <div v-else class="thumb-placeholder">{{ course.category?.charAt(0) }}</div>
    </div>

    <!-- 내용 -->
    <div class="card-body">
      <span class="badge" :class="badgeClass">{{ course.category }}</span>
      <h3 class="card-title">{{ course.title }}</h3>
      <div class="card-meta">
        <span class="instructor">{{ course.instructorName }}</span>
        <span class="price">₩{{ Number(course.price).toLocaleString() }}</span>
      </div>
      <div class="card-footer">
        <span class="enrolled">수강생 {{ course.enrollmentCount?.toLocaleString() }}명</span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { courseApi } from '@/api/course.js'

const props = defineProps({
  course: { type: Object, required: true }
})

// 업로드된 자산 미리보기. 404/500이면 onerror가 발생해 기본 이미지로 넘어간다.
const assetFailed = ref(false)
const assetPreviewSrc = computed(() => courseApi.previewUrl(props.course.id))
watch(() => props.course.id, () => { assetFailed.value = false })

const categoryConfig = {
  '로고 / 브랜딩':    { bg: 'thumb-teal',   badge: 'badge-teal',   thumb: 'spring_boot' },
  'UX / UI 키트':{ bg: 'thumb-teal',   badge: 'badge-teal',   thumb: 'vue_js' },
  '일러스트':   { bg: 'thumb-blue',   badge: 'badge-blue',   thumb: 'docker' },
  '아이콘':   { bg: 'thumb-purple', badge: 'badge-purple', thumb: 'python' },
  '템플릿':       { bg: 'thumb-pink',   badge: 'badge-pink',   thumb: 'generative_ai' },
}

const config = computed(() => categoryConfig[props.course.category] || { bg: 'thumb-gray', badge: 'badge-gray' })
const thumbBg = computed(() => config.value.bg)
const badgeClass = computed(() => config.value.badge)

// 썸네일 이미지 동적 import
const thumbSrc = computed(() => {
  const key = props.course.thumbnail || config.value.thumb
  if (!key) return null
  try {
    return new URL(`../assets/images/courses/${key}.png`, import.meta.url).href
  } catch {
    return null
  }
})
</script>

<style scoped>
.course-card {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: var(--transition);
  cursor: pointer;
}
.course-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-border-hover);
}
.card-thumb {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.thumb-teal   { background: #E1F5EE; }
.thumb-blue   { background: #E6F1FB; }
.thumb-amber  { background: #FAEEDA; }
.thumb-purple { background: #EEEDFE; }
.thumb-pink   { background: #FBEAF0; }
.thumb-gray   { background: #F1EFE8; }
.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 16px;
}
/* 실제 업로드 이미지는 카드를 꽉 채운다 */
.thumb-cover {
  object-fit: cover;
  padding: 0;
}
.thumb-placeholder {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-text-muted);
}
.card-body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.4;
}
.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.instructor {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.price {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}
.card-footer {
  margin-top: 2px;
}
.enrolled {
  font-size: 11px;
  color: var(--color-text-muted);
}
</style>
