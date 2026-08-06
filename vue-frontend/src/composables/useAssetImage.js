import { ref, watch, onBeforeUnmount, unref } from 'vue'
import { courseApi } from '@/api/course.js'

/**
 * 인증이 필요한 디자인 미리보기 이미지를 blob 으로 받아 objectURL 로 노출한다.
 *
 * 목록에서 카드마다 같은 이미지를 다시 받지 않도록 모듈 수준에서 캐싱한다.
 * 캐시는 페이지를 벗어나면 사라지는 수준이면 충분하므로 단순 Map 으로 둔다.
 */
const cache = new Map() // designId -> objectURL

export function useAssetImage(designId, hasAsset) {
  const src = ref(null)
  const loading = ref(false)
  const failed = ref(false)

  // 이 컴포넌트가 직접 만든 objectURL 만 해제한다.
  // 캐시에 올라간 것은 다른 카드도 쓰고 있으므로 건드리지 않는다.
  let ownedUrl = null

  async function load() {
    const id = unref(designId)
    const enabled = unref(hasAsset)

    src.value = null
    failed.value = false

    if (!id || !enabled) return

    if (cache.has(id)) {
      src.value = cache.get(id)
      return
    }

    loading.value = true
    try {
      const res = await courseApi.fetchPreview(id)
      const url = URL.createObjectURL(res.data)
      cache.set(id, url)
      ownedUrl = url
      src.value = url
    } catch (e) {
      console.error('[useAssetImage] preview load failed:', unref(designId), e)
      failed.value = true
    } finally {
      loading.value = false
    }
  }

  watch([() => unref(designId), () => unref(hasAsset)], load, { immediate: true })

  onBeforeUnmount(() => {
    // 캐시에 남겨두면 목록 재진입 시 재요청을 아낄 수 있다.
    // 메모리가 신경 쓰이면 아래 두 줄의 주석을 풀면 된다.
    // if (ownedUrl) { URL.revokeObjectURL(ownedUrl); cache.delete(unref(designId)) }
    ownedUrl = null
  })

  return { src, loading, failed }
}

/** 디자인 삭제·교체 후 캐시를 비울 때 사용 */
export function invalidateAssetCache(designId) {
  const url = cache.get(designId)
  if (url) {
    URL.revokeObjectURL(url)
    cache.delete(designId)
  }
}
