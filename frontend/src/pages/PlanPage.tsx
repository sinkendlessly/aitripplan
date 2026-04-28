import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { usePlanStream } from '../hooks/usePlanStream'
import { parsePlanData } from '../services/parser'
import ProgressTimeline from '../components/ProgressTimeline'
import RouteView from '../components/RouteView'
import ItineraryView from '../components/ItineraryView'
import BudgetView from '../components/BudgetView'
import { Loader2 } from 'lucide-react'
import type { ParsedPlanData } from '../types'

export default function PlanPage() {
  const { planId } = useParams<{ planId: string }>()
  const { events, plan, isComplete, error, isReconnecting, stalled, connect } = usePlanStream()
  const [parsed, setParsed] = useState<ParsedPlanData | null>(null)

  useEffect(() => {
    if (planId) connect(planId)
  }, [planId, connect])

  useEffect(() => {
    if (plan) {
      setParsed(parsePlanData(plan.routeResult, plan.itineraryResult, plan.budgetResult))
    }
  }, [plan])

  if (!planId) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-gray-400">
        <p>缺少规划 ID</p>
        <Link to="/" className="btn-ghost mt-4">返回首页</Link>
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h1 className="font-display text-xl font-bold text-gray-800">规划进行中</h1>
        <p className="text-sm text-gray-400 mt-1">规划 ID: {planId.slice(0, 8)}...</p>
      </div>

      {/* Reconnecting */}
      {isReconnecting && (
        <div className="mb-4 px-4 py-2 bg-sand-100 border border-sand-200/50 rounded-xl flex items-center gap-2 text-sm text-sand-600">
          <div className="w-3 h-3 rounded-full border-2 border-sand-400 border-t-transparent animate-spin" />
          连接中断，正在重连...
        </div>
      )}

      {/* Stalled */}
      {stalled && !isComplete && !error && (
        <div className="mb-4 px-4 py-2 bg-amber-50 border border-amber-200/50 rounded-xl flex items-center gap-2 text-sm text-amber-700">
          规划引擎响应较慢，请耐心等待...
        </div>
      )}

      {/* Progress */}
      <div className="mb-6">
        <ProgressTimeline events={events} />
      </div>

      {/* Error */}
      {error && (
        <div className="card p-5 border-coral-200/50 mb-6">
          <p className="text-sm text-coral-600 font-medium">规划出错</p>
          <p className="text-sm text-gray-500 mt-2">{error}</p>
          <Link to="/" className="btn-ghost mt-3 text-sm">重新规划</Link>
        </div>
      )}

      {/* Loading */}
      {!isComplete && !error && events.length === 0 && (
        <div className="card p-12 flex flex-col items-center justify-center text-gray-400">
          <Loader2 size={32} className="animate-spin mb-3 text-sand-400" />
          <p className="text-sm">正在连接规划引擎...</p>
        </div>
      )}

      {/* Results */}
      {parsed && (
        <div className="space-y-6 animate-fade-in">
          {parsed.route && <RouteView route={parsed.route} />}
          {parsed.itinerary && <ItineraryView itinerary={parsed.itinerary} />}
          {parsed.budget && <BudgetView budget={parsed.budget} />}

          {/* If parsed objects are empty but plan exists, show raw text fallback */}
          {!parsed.route && !parsed.itinerary && !parsed.budget && plan && (
            <div className="card p-5">
              <h3 className="text-sm font-semibold text-gray-500 uppercase mb-3">规划结果</h3>
              {plan.routeResult && (
                <pre className="text-xs text-gray-600 whitespace-pre-wrap font-sans">{plan.routeResult}</pre>
              )}
              {plan.itineraryResult && (
                <pre className="text-xs text-gray-600 whitespace-pre-wrap font-sans mt-3">{plan.itineraryResult}</pre>
              )}
              {plan.budgetResult && (
                <pre className="text-xs text-gray-600 whitespace-pre-wrap font-sans mt-3">{plan.budgetResult}</pre>
              )}
            </div>
          )}

          {plan?.executionTime && (
            <p className="text-center text-xs text-gray-400">
              规划完成，耗时 {(plan.executionTime / 1000).toFixed(1)} 秒
            </p>
          )}
        </div>
      )}
    </div>
  )
}
