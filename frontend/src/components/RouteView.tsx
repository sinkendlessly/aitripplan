import type { RouteResult } from '../types'
import { MapPin, ArrowRight, Car, Train, Plane, Ship } from 'lucide-react'

const transportIcon: Record<string, typeof Car> = {
  driving: Car,
  train: Train,
  flight: Plane,
  ferry: Ship,
}

function SegmentDot({ isFirst, isLast }: { isFirst: boolean; isLast: boolean }) {
  if (isFirst) return <div className="w-4 h-4 rounded-full bg-coral-400 border-2 border-white shadow-sm flex-shrink-0" />
  if (isLast) return <div className="w-4 h-4 rounded-full bg-sage-500 border-2 border-white shadow-sm flex-shrink-0" />
  return <div className="w-3 h-3 rounded-full bg-sand-300 border-2 border-white flex-shrink-0" />
}

export default function RouteView({ route }: { route: RouteResult }) {
  return (
    <div className="card p-5 animate-fade-in">
      <div className="flex items-center gap-2 mb-4">
        <MapPin size={18} className="text-coral-500" />
        <h2 className="font-display font-semibold text-gray-800">路线规划</h2>
      </div>

      <div className="flex items-center gap-3 mb-5 text-sm">
        <span className="tag-coral">{route.origin}</span>
        <ArrowRight size={16} className="text-sand-400" />
        <span className="tag-sage">{route.destination}</span>
        <span className="ml-auto text-xs text-gray-400">总距离 {route.totalDistanceKm} km</span>
      </div>

      <div className="space-y-0">
        {route.segments.map((seg, i) => {
          const Icon = transportIcon[seg.transportMode] || Car
          return (
            <div key={i} className="flex items-start gap-3 pb-4 last:pb-0">
              <div className="flex flex-col items-center gap-1">
                <SegmentDot isFirst={i === 0} isLast={i === route.segments.length - 1} />
                {i < route.segments.length - 1 && <div className="w-0.5 flex-1 bg-sand-200" />}
              </div>
              <div className="flex-1 pt-0.5">
                <div className="flex items-center gap-2">
                  <Icon size={14} className="text-gray-400" />
                  <p className="text-sm font-medium text-gray-700">
                    {seg.from} → {seg.to}
                  </p>
                </div>
                <p className="text-xs text-gray-400 mt-0.5">
                  {seg.distanceKm} km · {seg.durationMin} 分钟 · {seg.transportMode}
                </p>
                {seg.description && (
                  <p className="text-xs text-gray-500 mt-1">{seg.description}</p>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
