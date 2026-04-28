import type { ItineraryResult } from '../types'
import { CalendarDays, Sun, Sunset, Moon, Lightbulb } from 'lucide-react'

const periodIcon = (period: string) => {
  switch (period) {
    case 'morning': return Sun
    case 'afternoon': return Sunset
    case 'evening': return Moon
    default: return Sun
  }
}

const periodLabel: Record<string, string> = {
  morning: '上午',
  afternoon: '下午',
  evening: '晚上',
}

const periodColor: Record<string, string> = {
  morning: 'bg-amber-50 border-amber-200/50 text-amber-700',
  afternoon: 'bg-orange-50 border-orange-200/50 text-orange-700',
  evening: 'bg-indigo-50 border-indigo-200/50 text-indigo-700',
}

export default function ItineraryView({ itinerary }: { itinerary: ItineraryResult }) {
  return (
    <div className="card p-5 animate-fade-in">
      <div className="flex items-center gap-2 mb-4">
        <CalendarDays size={18} className="text-sage-500" />
        <h2 className="font-display font-semibold text-gray-800">行程安排</h2>
        <span className="ml-auto text-xs text-gray-400">{itinerary.totalDays} 天行程</span>
      </div>

      <div className="space-y-4">
        {itinerary.days.map(day => (
          <div key={day.day} className="border border-sand-200/50 rounded-xl overflow-hidden">
            <div className="bg-sand-100/50 px-4 py-2.5 flex items-center gap-2">
              <div className="w-6 h-6 rounded-full bg-sand-200 text-sand-700 flex items-center justify-center text-xs font-bold">
                {day.day}
              </div>
              <span className="text-sm font-semibold text-gray-700">第 {day.day} 天</span>
              {day.theme && (
                <span className="tag-sand ml-1">{day.theme}</span>
              )}
            </div>
            <div className="p-4 space-y-3">
              {(['morning', 'afternoon', 'evening'] as const).map(period => {
                const activities = day[period] || []
                if (activities.length === 0) return null
                const Icon = periodIcon(period)
                return (
                  <div key={period} className="flex gap-3">
                    <div className={`flex-shrink-0 w-7 h-7 rounded-lg border flex items-center justify-center ${periodColor[period]}`}>
                      <Icon size={14} />
                    </div>
                    <div className="flex-1">
                      <p className="text-xs font-semibold text-gray-500 mb-1.5">{periodLabel[period]}</p>
                      <div className="space-y-2">
                        {activities.map((act, j) => (
                          <div key={j} className="text-sm">
                            <p className="font-medium text-gray-700">{act.title}</p>
                            <p className="text-xs text-gray-400 mt-0.5">{act.description}</p>
                            {act.tips && (
                              <p className="text-xs text-sand-500 mt-0.5 flex items-center gap-1">
                                <Lightbulb size={12} className="text-coral-400 flex-shrink-0" />
                                {act.tips}
                              </p>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
