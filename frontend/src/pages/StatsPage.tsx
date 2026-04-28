import { useEffect, useState } from 'react'
import { getStats } from '../services/api'
import type { StatsData } from '../types'
import { BarChart3, Route, CheckCircle2, DollarSign, Loader2 } from 'lucide-react'

export default function StatsPage() {
  const [stats, setStats] = useState<StatsData | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getStats()
      .then(setStats)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="card p-12 flex flex-col items-center justify-center text-gray-400">
          <Loader2 size={28} className="animate-spin mb-3 text-sand-400" />
          <p className="text-sm">加载中...</p>
        </div>
      </div>
    )
  }

  if (!stats) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="card p-12 text-center">
          <p className="text-gray-400">暂无统计数据</p>
        </div>
      </div>
    )
  }

  const cards = [
    { icon: Route, label: '总规划数', value: stats.totalPlans, color: 'text-sand-500', bg: 'bg-sand-100' },
    { icon: CheckCircle2, label: '成功率', value: `${(stats.successRate * 100).toFixed(1)}%`, color: 'text-sage-500', bg: 'bg-sage-100' },
    { icon: DollarSign, label: '平均预算', value: stats.averageCost ? `¥${stats.averageCost.toLocaleString()}` : '暂无', color: 'text-coral-400', bg: 'bg-coral-100' },
  ]

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center gap-2 mb-6">
        <BarChart3 size={20} className="text-sand-500" />
        <h1 className="font-display text-xl font-bold text-gray-800">数据统计</h1>
      </div>

      <div className="grid grid-cols-3 gap-4 mb-8">
        {cards.map(card => {
          const Icon = card.icon
          return (
            <div key={card.label} className="card p-4 text-center">
              <div className={`w-10 h-10 rounded-xl ${card.bg} flex items-center justify-center mx-auto mb-2`}>
                <Icon size={20} className={card.color} />
              </div>
              <p className="text-2xl font-bold text-gray-800">{card.value}</p>
              <p className="text-xs text-gray-400 mt-1">{card.label}</p>
            </div>
          )
        })}
      </div>

      {/* Hot destinations: backend sends array of [name, count] tuples */}
      {stats.hotDestinations && stats.hotDestinations.length > 0 && (
        <div className="card p-5">
          <h2 className="font-display font-semibold text-gray-800 mb-4">热门目的地</h2>
          <div className="space-y-3">
            {stats.hotDestinations.map(([name, count], i) => {
              const maxCount = Math.max(...stats.hotDestinations.map(([, c]) => c))
              const barWidth = (count / maxCount) * 100
              return (
                <div key={i} className="flex items-center gap-3">
                  <span className="text-sm font-medium text-gray-600 w-6">{i + 1}</span>
                  <div className="flex-1">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-sm text-gray-700">{name}</span>
                      <span className="text-xs text-gray-400">{count} 次</span>
                    </div>
                    <div className="h-2 bg-sand-100 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-gradient-to-r from-sand-400 to-coral-400 rounded-full transition-all duration-1000"
                        style={{ width: `${barWidth}%` }}
                      />
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
