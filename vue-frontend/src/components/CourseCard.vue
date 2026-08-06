<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  course: { type: Object, required: true }
})

// 백엔드가 내려준 thumbnailUrl 을 그대로 쓴다.
// hasAsset 이 false 면 아예 요청하지 않으므로 불필요한 404가 나지 않는다.
const assetFailed = ref(false)
const assetPreviewSrc = computed(() =>
  props.course.hasAsset ? props.course.thumbnailUrl : null
)
watch(() => props.course.id, () => { assetFailed.value = false })

const categoryConfig = {
  '로고 / 브랜딩': { bg: 'thumb-teal',   badge: 'badge-teal',   thumb: 'spring_boot' },
  'UX / UI 키트':  { bg: 'thumb-teal',   badge: 'badge-teal',   thumb: 'vue_js' },
  '일러스트':      { bg: 'thumb-blue',   badge: 'badge-blue',   thumb: 'docker' },
  '아이콘':        { bg: 'thumb-purple', badge: 'badge-purple', thumb: 'python' },
  '템플릿':        { bg: 'thumb-pink',   badge: 'badge-pink',   thumb: 'generative_ai' },
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