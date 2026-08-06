<template>
  <div class="sales-tab">
    <div v-if="loading" class="loading-row">
      <div v-for="i in 2" :key="i" class="skeleton-card"></div>
    </div>

    <template v-else-if="sales">
      <div class="summary-cards">
        <div class="summary-card">
          <div class="summary-label">총 매출</div>
          <div class="summary-value">{{ formatPrice(sales.totalRevenue) }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">총 판매 건수</div>
          <div class="summary-value">{{ sales.totalSalesCount }}건</div>
        </div>
      </div>

      <div class="sales-list">
        <div v-for="item in sales.byDesign" :key="item.designId" class="sales-row">
          <span class="design-title">{{ item.title }}</span>
          <span class="design-count">{{ item.salesCount }}건</span>
          <span class="design-revenue">{{ formatPrice(item.revenue) }}</span>
        </div>
      </div>
    </template>

    <p v-else class="empty-text">아직 판매 내역이 없습니다.</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { salesApi } from '@/api/sales.js'

const sales = ref(null)
const loading = ref(true)

function formatPrice(v) {
  return `${Number(v ?? 0).toLocaleString()}원`
}

onMounted(async () => {
  try {
    const res = await salesApi.getMySales()
    sales.value = res.data?.data ?? res.data
  } catch (e) {
    console.error('[SalesTab] 판매 통계 조회 실패:', e)
  } finally {
    loading.value = false
  }
})
</script>