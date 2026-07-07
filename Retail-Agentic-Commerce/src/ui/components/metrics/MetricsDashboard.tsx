"use client";

import { useMetrics } from "@/hooks/useMetrics";
import { MetricsHeader } from "@/components/metrics/MetricsHeader";
import { KPIPanel } from "@/components/metrics/panels/KPIPanel";
import { RevenuePanel } from "@/components/metrics/panels/RevenuePanel";
import { AgentPerformancePanel } from "@/components/metrics/panels/AgentPerformancePanel";
import { RecommendationAttributionPanel } from "@/components/metrics/panels/RecommendationAttributionPanel";
import { PromotionPanel } from "@/components/metrics/panels/PromotionPanel";
import { ProductHealthPanel } from "@/components/metrics/panels/ProductHealthPanel";

/**
 * Main dashboard container with grid layout
 */
export function MetricsDashboard() {
  const { state, setTimeRange, refresh } = useMetrics();
  const {
    timeRange,
    isLoading,
    lastUpdated,
    kpis,
    revenueData,
    agentPerformance,
    recommendationAttribution,
    promotionBreakdown,
    productHealth,
  } = state;

  return (
    <div className="dashboard-grid">
      {/* Header */}
      <MetricsHeader
        timeRange={timeRange}
        onTimeRangeChange={setTimeRange}
        onRefresh={refresh}
        isLoading={isLoading}
        lastUpdated={lastUpdated}
      />

      {/* KPI Row */}
      <KPIPanel kpis={kpis} isLoading={isLoading} />

      {/* Revenue Chart */}
      <RevenuePanel data={revenueData} timeRange={timeRange} isLoading={isLoading} />

      {/* Two Column Row: Agent Performance & Promotions */}
      <div className="dashboard-row two-col">
        <AgentPerformancePanel data={agentPerformance} isLoading={isLoading} />
        <PromotionPanel data={promotionBreakdown} isLoading={isLoading} />
      </div>

      <RecommendationAttributionPanel data={recommendationAttribution} isLoading={isLoading} />

      {/* Product Health Table */}
      <ProductHealthPanel data={productHealth} isLoading={isLoading} />
    </div>
  );
}
