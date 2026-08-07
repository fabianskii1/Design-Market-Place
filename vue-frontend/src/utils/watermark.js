/**
 * 브라우저에서 이미지에 워터마크를 합성한다.
 *
 * 업로드 직전에 캔버스로 처리하므로 백엔드 수정이 필요 없다.
 * 서버에는 이미 워터마크가 박힌 파일이 저장된다.
 *
 * 두 가지를 함께 넣는다.
 *  1. 가시적  — 디자이너 이름을 대각선으로 반복 배치. 무단 사용 억제용.
 *  2. 비가시적 — 픽셀 최하위 비트에 식별 문자열을 숨김. 유출 추적용.
 *
 * 비가시적 워터마크는 무손실 포맷에서만 살아남는다. JPEG로 저장하면 파괴되므로
 * 가능한 한 PNG로 내보내고, 용량이 한계를 넘을 때만 JPEG로 떨어뜨린다.
 */

const MAX_EDGE = 2000          // 긴 변 상한 (용량 억제)
const MAX_OUTPUT_BYTES = 9 * 1024 * 1024
const OPACITY = 0.28
const ANGLE = -Math.PI / 6

// ─────────────────────────────────────────────────────────
// LSB (비가시적)
// ─────────────────────────────────────────────────────────

const MAGIC = [0x44, 0x4d, 0x50, 0x57] // "DMPW"
const HEADER_BYTES = 8
const MAX_PAYLOAD_BYTES = 512

function buildFrame(payload) {
  const bytes = new TextEncoder().encode(payload)
  if (bytes.length > MAX_PAYLOAD_BYTES) {
    throw new Error('워터마크 페이로드가 너무 깁니다.')
  }
  const frame = new Uint8Array(HEADER_BYTES + bytes.length)
  frame.set(MAGIC, 0)
  const len = bytes.length
  frame[4] = (len >>> 24) & 0xff
  frame[5] = (len >>> 16) & 0xff
  frame[6] = (len >>> 8) & 0xff
  frame[7] = len & 0xff
  frame.set(bytes, HEADER_BYTES)
  return frame
}

/** ImageData의 파랑 채널 최하위 비트에 프레임을 심는다 */
function embedLsb(imageData, payload) {
  const frame = buildFrame(payload)
  const totalBits = frame.length * 8
  const pixels = imageData.width * imageData.height

  if (totalBits > pixels) {
    throw new Error('이미지가 너무 작아 워터마크를 삽입할 수 없습니다.')
  }

  const d = imageData.data
  for (let i = 0; i < totalBits; i++) {
    const bit = (frame[i >> 3] >> (7 - (i & 7))) & 1
    const blueIndex = i * 4 + 2      // R,G,B,A 중 B
    d[blueIndex] = (d[blueIndex] & 0xfe) | bit
  }
  return imageData
}

/** 이미지에서 숨겨진 문자열을 꺼낸다. 없으면 null */
export function extractLsb(imageData) {
  const d = imageData.data
  const pixels = imageData.width * imageData.height
  if (pixels < HEADER_BYTES * 8) return null

  const readBytes = (startBit, count) => {
    const buf = new Uint8Array(count)
    for (let i = 0; i < count * 8; i++) {
      const bit = d[(startBit + i) * 4 + 2] & 1
      buf[i >> 3] |= bit << (7 - (i & 7))
    }
    return buf
  }

  const header = readBytes(0, HEADER_BYTES)
  for (let i = 0; i < 4; i++) {
    if (header[i] !== MAGIC[i]) return null
  }

  const len = (header[4] << 24) | (header[5] << 16) | (header[6] << 8) | header[7]
  if (len <= 0 || len > MAX_PAYLOAD_BYTES) return null
  if ((HEADER_BYTES + len) * 8 > pixels) return null

  return new TextDecoder().decode(readBytes(HEADER_BYTES * 8, len))
}

// ─────────────────────────────────────────────────────────
// 가시적
// ─────────────────────────────────────────────────────────

function drawVisible(ctx, width, height, text) {
  const fontSize = Math.max(14, Math.round(width / 22))
  ctx.save()
  ctx.font = `bold ${fontSize}px -apple-system, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif`
  ctx.textBaseline = 'middle'
  ctx.globalAlpha = OPACITY

  const textWidth = ctx.measureText(text).width
  const stepX = textWidth + fontSize * 2.5
  const stepY = fontSize * 4

  ctx.translate(width / 2, height / 2)
  ctx.rotate(ANGLE)
  ctx.translate(-width / 2, -height / 2)

  // 회전 후에도 모서리가 비지 않도록 캔버스보다 넓게 채운다
  const margin = Math.max(width, height)
  for (let y = -margin; y < height + margin; y += stepY) {
    for (let x = -margin; x < width + margin; x += stepX) {
      ctx.fillStyle = 'rgba(0, 0, 0, 0.55)'
      ctx.fillText(text, x + 2, y + 2)
      ctx.fillStyle = '#ffffff'
      ctx.fillText(text, x, y)
    }
  }
  ctx.restore()

  // 하단 고정 라벨
  const barHeight = Math.max(26, Math.round(height / 18))
  const labelSize = Math.max(11, Math.round(barHeight * 0.45))
  ctx.save()
  ctx.globalAlpha = 0.5
  ctx.fillStyle = '#000000'
  ctx.fillRect(0, height - barHeight, width, barHeight)
  ctx.globalAlpha = 1
  ctx.fillStyle = '#ffffff'
  ctx.font = `${labelSize}px -apple-system, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif`
  ctx.textBaseline = 'middle'
  ctx.fillText(`© ${text}`, 12, height - barHeight / 2)
  ctx.restore()
}

// ─────────────────────────────────────────────────────────
// 공개 API
// ─────────────────────────────────────────────────────────

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => { URL.revokeObjectURL(url); resolve(img) }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('이미지를 읽을 수 없습니다.')) }
    img.src = url
  })
}

function toBlob(canvas, type, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('이미지 변환에 실패했습니다.'))),
      type,
      quality
    )
  })
}

/**
 * 워터마크를 합성한 새 File을 돌려준다.
 *
 * @param {File}   file          원본 이미지
 * @param {string} designerName  가시적 워터마크에 쓸 이름
 * @param {object} [options]
 * @param {string} [options.payload]  비가시적으로 심을 문자열
 * @returns {Promise<{file: File, invisible: boolean, width: number, height: number}>}
 */
export async function applyWatermark(file, designerName, options = {}) {
  const img = await loadImage(file)

  const scale = Math.min(1, MAX_EDGE / Math.max(img.naturalWidth, img.naturalHeight))
  const width = Math.max(1, Math.round(img.naturalWidth * scale))
  const height = Math.max(1, Math.round(img.naturalHeight * scale))

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d', { willReadFrequently: true })

  // 투명 PNG가 검게 나오지 않도록 흰 배경을 먼저 깐다
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)
  ctx.drawImage(img, 0, 0, width, height)

  drawVisible(ctx, width, height, designerName)

  // 비가시적 워터마크는 가시적 합성이 끝난 뒤 마지막에 심는다
  let invisible = false
  if (options.payload) {
    try {
      const imageData = ctx.getImageData(0, 0, width, height)
      ctx.putImageData(embedLsb(imageData, options.payload), 0, 0)
      invisible = true
    } catch (e) {
      console.warn('[watermark] 비가시적 워터마크 삽입 생략:', e.message)
    }
  }

  // 무손실 우선. 용량이 크면 JPEG로 떨어뜨리고 비가시적 워터마크는 포기한다.
  let blob = await toBlob(canvas, 'image/png')
  let type = 'image/png'
  let extension = '.png'

  if (blob.size > MAX_OUTPUT_BYTES) {
    blob = await toBlob(canvas, 'image/jpeg', 0.9)
    type = 'image/jpeg'
    extension = '.jpg'
    invisible = false
    console.warn('[watermark] 용량 초과로 JPEG 저장 — 비가시적 워터마크는 유지되지 않습니다.')
  }

  const baseName = (file.name || 'design').replace(/\.[^.]+$/, '')
  const watermarked = new File([blob], `${baseName}_wm${extension}`, { type })

  return { file: watermarked, invisible, width, height }
}

/** 업로드된 이미지에서 숨겨진 워터마크를 읽어 출처를 확인한다 (검증 도구용) */
export async function readWatermark(file) {
  const img = await loadImage(file)
  const canvas = document.createElement('canvas')
  canvas.width = img.naturalWidth
  canvas.height = img.naturalHeight
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  ctx.drawImage(img, 0, 0)
  return extractLsb(ctx.getImageData(0, 0, canvas.width, canvas.height))
}
