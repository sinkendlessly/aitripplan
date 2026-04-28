import type { BudgetResult } from '../types'
import { Wallet, TrendingUp, Lightbulb } from 'lucide-react'

const categoryColors: Record<string, string> = {
  transport: 'bg-coral-400',
  accommodation: 'bg-sand-500',
  food: 'bg-sage-400',
  activities: 'bg-coral-300',
  shopping: 'bg-sand-400',
  other: 'bg-gray-300',
  default: 'bg-sand-400',
}

export default function BudgetView({ budget }: { budget: BudgetResult }) {
  return (
    <div className="card p-5 animate-fade-in">
      <div className="flex items-center gap-2 mb-4">
        <Wallet size={18} className="text-sand-600" />
        <h2 className="font-display font-semibold text-gray-800">预算估算</h2>
        <span className="ml-auto text-lg font-display font-bold text-sand-700">
          ¥{budget.total.toLocaleString()}
        </span>
      </div>

      {/* Breakdown bar */}
      <div className="mb-5">
        <div className="flex h-3 rounded-full overflow-hidden bg-sand-100">
          {budget.breakdown.map((item, i) => (
            <div
              key={i}
              style={{ width: `${item.percentage}%` }}
              className={categoryColors[item.category] || categoryColors.default}
              title={`${item.category}: ${item.percentage.toFixed(0)}%`}
            />
          ))}
        </div>
        <div className="grid grid-cols-2 gap-2 mt-3">
          {budget.breakdown.map((item, i) => (
            <div key={i} className="flex items-center gap-2 text-xs">
              <div className={`w-2.5 h-2.5 rounded-full ${categoryColors[item.category] || categoryColors.default}`} />
              <span className="text-gray-500">{item.category}</span>
              <span className="ml-auto font-medium text-gray-700">¥{item.amount.toLocaleString()}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Tiers */}
      {budget.tiers && budget.tiers.length > 0 && (
        <div className="mb-4">
          <div className="flex items-center gap-1.5 mb-2">
            <TrendingUp size={14} className="text-sand-500" />
            <span className="text-xs font-semibold text-gray-500 uppercase">预算档次</span>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {budget.tiers.map((tier, i) => (
              <div key={i} className="border border-sand-200/50 rounded-lg p-2.5 text-center">
                <p className="text-xs text-gray-500">{tier.name}</p>
                <p className="text-sm font-bold text-sand-700">¥{tier.total.toLocaleString()}</p>
                <p className="text-[10px] text-gray-400 mt-0.5">{tier.description}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tips */}
      {budget.optimizationTips && budget.optimizationTips.length > 0 && (
        <div>
          <div className="flex items-center gap-1.5 mb-2">
            <Lightbulb size={14} className="text-coral-400" />
            <span className="text-xs font-semibold text-gray-500 uppercase">省钱建议</span>
          </div>
          <ul className="space-y-1">
            {budget.optimizationTips.map((tip, i) => (
              <li key={i} className="text-xs text-gray-500 flex items-start gap-2">
                <span className="text-coral-400 mt-0.5">•</span>
                {tip}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
