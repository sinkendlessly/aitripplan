import type { StreamEvent } from '../types'
import { Sparkles, MapPin, CalendarRange, Wallet, CheckCircle2, AlertCircle } from 'lucide-react'

const eventConfig: Record<string, { icon: typeof MapPin; label: string; color: string }> = {
  thinking: { icon: Sparkles, label: 'AI 思考中', color: 'text-sand-500' },
  agent_start: { icon: Sparkles, label: '启动规划引擎', color: 'text-sand-600' },
  route_progress: { icon: MapPin, label: '规划路线', color: 'text-coral-500' },
  itinerary_progress: { icon: CalendarRange, label: '安排行程', color: 'text-sage-500' },
  budget_progress: { icon: Wallet, label: '计算预算', color: 'text-sand-600' },
  complete: { icon: CheckCircle2, label: '规划完成', color: 'text-sage-600' },
  error: { icon: AlertCircle, label: '出现错误', color: 'text-coral-600' },
}

export default function ProgressTimeline({ events }: { events: StreamEvent[] }) {
  const last = events[events.length - 1]

  return (
    <div className="card p-5">
      <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-3">规划进度</h3>
      <div className="relative">
        <div className="absolute left-4 top-0 bottom-0 w-px bg-sand-200/60" />
        <div className="space-y-1">
          {events.map((event, i) => {
            const config = eventConfig[event.type] || eventConfig.thinking
            const Icon = config.icon
            return (
              <div key={`${event.type}-${i}`} className="flex items-start gap-3 py-2 animate-fade-in">
                <div className={`flex-shrink-0 w-8 h-8 rounded-full bg-white/80 border border-sand-200/50 flex items-center justify-center ${config.color}`}>
                  <Icon size={14} />
                </div>
                <div className="flex-1 min-w-0 pt-1">
                  <p className="text-sm font-medium text-gray-700">{config.label}</p>
                  {event.content && (
                    <p className="text-xs text-gray-400 mt-0.5 line-clamp-2">{event.content}</p>
                  )}
                  {event.agent && (
                    <p className="text-[11px] text-sand-400 mt-0.5">{event.agent}</p>
                  )}
                </div>
              </div>
            )
          })}
        </div>
        {last && last.type !== 'complete' && last.type !== 'error' && (
          <div className="flex items-center gap-3 py-2">
            <div className="flex-shrink-0 w-8 h-8 rounded-full bg-sand-100 border border-sand-300/50 flex items-center justify-center">
              <div className="w-2 h-2 rounded-full bg-sand-400 animate-pulse-soft" />
            </div>
            <p className="text-sm text-sand-500">处理中...</p>
          </div>
        )}
      </div>
    </div>
  )
}
