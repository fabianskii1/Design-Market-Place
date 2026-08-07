<template>
  <div class="sales-tab">
    <div class="summary-cards">
      <div class="summary-card">
        <div class="summary-label">총 판매 건수</div>
        <div class="summary-value">{{ totalSalesCount }}건</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">총 매출액</div>
        <div class="summary-value">{{ formatPrice(totalRevenue) }}</div>
      </div>
    </div>

    <div v-if="monthlyBreakdown.length" class="monthly-section fade-in">
      <div class="monthly-title">월별 매출</div>
      <div class="monthly-list">
        <div v-for="m in monthlyBreakdown" :key="m.month" class="monthly-row">
          <span class="monthly-month">{{ m.month }}</span>
          <span class="monthly-count">{{ m.salesCount }}건</span>
          <span class="monthly-revenue">{{ formatPrice(m.revenue) }}</span>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-row sales-loading">
      <div v-for="i in 3" :key="i" class="skeleton-card">
        <div class="skeleton-thumb"></div>
        <div class="skeleton-body">
          <div class="skeleton-line short"></div>
          <div class="skeleton-line"></div>
        </div>
      </div>
    </div>

    <div v-else-if="sales.length" class="sales-list fade-in">
      <div v-for="item in sales" :key="item.id" class="sales-card">
        <div class="sales-card-top">
          <h4 class="sales-title">{{ item.title }}</h4>
          <span class="sales-badge">판매 {{ item.salesCount }}건</span>
        </div>

        <div class="sales-meta-grid">
          <div class="meta-box">
            <div class="meta-label">단가</div>
            <div class="meta-value">{{ formatPrice(item.price) }}</div>
          </div>
          <div class="meta-box">
            <div class="meta-label">판매 건수</div>
            <div class="meta-value">{{ item.salesCount }}건</div>
          </div>
          <div class="meta-box">
            <div class="meta-label">매출액</div>
            <div class="meta-value">{{ formatPrice(item.revenue) }}</div>
          </div>
        </div>

        <div v-if="item.salesByLicense?.length" class="license-breakdown">
          <div class="license-breakdown-title">라이선스별 매출</div>
          <div
            v-for="lic in item.salesByLicense"
            :key="lic.tier"
            class="license-breakdown-row"
          >
            <span class="license-breakdown-label">{{ tierLabel(lic.tier) }}</span>
            <span class="license-breakdown-count">{{ lic.count }}건</span>
            <span class="license-breakdown-revenue">{{ formatPrice(lic.revenue) }}</span>
          </div>
        </div>
      </div>
    </div>

    <p v-else-if="error" class="empty-text">{{ error }}</p>
    <p v-else class="empty-text">아직 판매 내역이 없습니다.</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { salesApi } from '@/api/sales.js'
import { LICENSE_TIERS } from '@/api/license.js'

const sales = ref([])
const loading = ref(true)
const error = ref('')
const monthlyBreakdown = ref([])

const totalSalesCount = computed(() =>
  sales.value.reduce((sum, item) => sum + (Number(item.salesCount) || 0), 0)
)

const totalRevenue = computed(() =>
  sales.value.reduce((sum, item) => sum + (Number(item.revenue) || 0), 0)
)

function formatPrice(price) {
  const value = Number(price ?? 0)
  if (Number.isNaN(value)) return '-'
  return `${value.toLocaleString()}원`
}

function tierLabel(tier) {
  return LICENSE_TIERS.find(t => t.tier === tier)?.label ?? tier
}

/**
 * 백엔드 응답 형태가 아직 확정되지 않았을 수 있어, 여러 필드명을 방어적으로 매핑한다.
 */
function normalizeSalesItem(raw, index) {
  return {
    id: raw.id ?? raw.courseId ?? raw.designId ?? index,
    title: raw.title ?? raw.courseTitle ?? raw.designTitle ?? raw.name ?? '이름 없음',
    price: raw.price ?? raw.unitPrice ?? 0,
    salesCount: raw.salesCount ?? raw.purchaseCount ?? raw.count ?? 0,
    revenue: raw.revenue ?? raw.totalRevenue ?? raw.totalAmount ?? raw.amount ?? 0,
    salesByLicense: raw.salesByLicense ?? []
  }
}

async function loadSales() {
  try {
    const res = await salesApi.getMySales()
    console.log('[SalesTab] sales response:', res.data)

    const items = res.data?.data?.items ?? []
    sales.value = items.map(normalizeSalesItem)
    monthlyBreakdown.value = res.data?.data?.monthlyBreakdown ?? []
  } catch (err) {
    console.error('[SalesTab] failed to load sales:', err)
    error.value = '판매 현황을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    loading.value = false
  }
}

onMounted(loadSales)
</script>

<style scoped>
.sales-tab {
  display: flex;
  flex-direction: column;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(160px, 220px));
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  box-shadow: var(--shadow-sm);
}

.summary-label {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.summary-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.sales-loading {
  margin-bottom: 20px;
}

.loading-row {
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
  height: 110px;
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

.sales-list {
  display: grid;
  gap: 18px;
}

.sales-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 22px;
  box-shadow: var(--shadow-sm);
}

.sales-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.sales-title {
  font-size: 18px;
  font-weight: 700;
}

.sales-badge {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.sales-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.meta-box {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  padding: 14px;
}

.meta-label {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 6px;
}

.meta-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.empty-text {
  color: var(--color-text-muted);
  font-size: 14px;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 992px) {
  .loading-row,
  .sales-meta-grid {
    grid-template-columns: 1fr;
  }

  .summary-cards {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .summary-cards {
    grid-template-columns: 1fr;
  }
}
</style>
