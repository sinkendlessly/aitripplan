import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getHistory } from '../services/api'
import type { TravelPlanHistory } from '../types'
import { Clock, MapPin, ChevronRight, Loader2 } from 'lucide-react'

export default function HistoryPage() {
  const [history, setHistory] = useState<TravelPlanHistory[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getHistory(page, 10)
      .then(data => {
        setHistory(data.content)
        setTotalPages(data.totalPages)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [page])

  function getStatusTag(status: string) {
    switch (status) {
      case 'SUCCESS':
        return <span className="tag-sage">已完成</span>
      case 'PARTIAL':
        return <span className="tag-sand">部分完成</span>
      case 'FAILED':
        return <span className="tag-coral">失败</span>
      default:
        return <span className="tag-sand">处理中</span>
    }
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex items-center gap-2 mb-6">
        <Clock size={20} className="text-sand-500" />
        <h1 className="font-display text-xl font-bold text-gray-800">历史规划</h1>
      </div>

      {loading ? (
        <div className="card p-12 flex flex-col items-center justify-center text-gray-400">
          <Loader2 size={28} className="animate-spin mb-3 text-sand-400" />
          <p className="text-sm">加载中...</p>
        </div>
      ) : history.length === 0 ? (
        <div className="card p-12 text-center">
          <p className="text-gray-400 mb-4">还没有规划记录</p>
          <Link to="/" className="btn-primary">开始第一次规划</Link>
        </div>
      ) : (
        <div className="space-y-3">
          {history.map(entry => (
            <Link
              key={entry.planId}
              to={`/plan/${entry.planId}`}
              className="card-hover p-4 flex items-center gap-4"
            >
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-sand-100 to-sand-200 flex items-center justify-center flex-shrink-0">
                <MapPin size={18} className="text-sand-500" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-700 truncate">
                  {entry.userRequest}
                </p>
                <div className="flex items-center gap-2 mt-1">
                  {getStatusTag(entry.status)}
                  <span className="text-xs text-gray-400">
                    {new Date(entry.createTime).toLocaleDateString('zh-CN')}
                  </span>
                  {entry.executionTime && (
                    <span className="text-xs text-gray-400">
                      {(entry.executionTime / 1000).toFixed(1)}s
                    </span>
                  )}
                </div>
              </div>
              <ChevronRight size={16} className="text-gray-300 flex-shrink-0" />
            </Link>
          ))}

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-4">
              {Array.from({ length: totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={`w-8 h-8 rounded-lg text-xs font-medium transition-all ${
                    i === page
                      ? 'bg-sand-200 text-sand-800'
                      : 'text-gray-400 hover:text-gray-600 hover:bg-sand-100'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
