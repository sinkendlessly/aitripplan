import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createPlan } from '../services/api'
import { Send, Users, Wallet, Sparkles } from 'lucide-react'

const QUICK_EXAMPLES = [
  '从北京到上海，3天旅行，预算5000元，喜欢美食和博物馆',
  '从成都到西安，2天文化之旅，预算3000元',
  '从杭州到苏州，4天休闲游，预算8000元，带老人出行',
  '从广州到三亚，5天度假，预算10000元',
]

export default function HomePage() {
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState('')
  const [budgetRaw, setBudgetRaw] = useState('')
  const [travelers, setTravelers] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!prompt.trim()) return

    setSubmitting(true)
    setError(null)

    try {
      const options: {
        budget?: number
        travelers?: number
      } = {}

      if (budgetRaw) {
        const parsed = parseInt(budgetRaw.replace(/[^0-9]/g, ''), 10)
        if (!isNaN(parsed)) options.budget = parsed
      }
      if (travelers > 1) options.travelers = travelers

      const result = await createPlan({
        prompt: prompt.trim(),
        options: Object.keys(options).length > 0 ? options : undefined,
      })

      navigate(`/plan/${result.planId}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交失败，请稍后重试')
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col min-h-full">
      <div className="flex-1 flex flex-col items-center justify-center px-4 py-12">
        <div className="max-w-2xl w-full">
          <div className="text-center mb-8">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-sand-400 to-coral-400 flex items-center justify-center mx-auto mb-4 shadow-lg shadow-sand-200/50 animate-float">
              <Sparkles size={28} className="text-white" />
            </div>
            <h1 className="font-display text-3xl font-bold text-gray-800 mb-2">
              你的智能旅行规划师
            </h1>
            <p className="text-gray-400 text-sm max-w-md mx-auto">
              告诉我你想去哪里、玩几天、预算多少，AI 将为你量身定制完整的旅行方案
            </p>
          </div>

          <form onSubmit={handleSubmit} className="card p-6 space-y-4">
            <div>
              <label className="label">描述你的旅行需求</label>
              <textarea
                value={prompt}
                onChange={e => setPrompt(e.target.value)}
                placeholder="例如：从北京到上海，3天旅行，预算5000元，喜欢美食和博物馆..."
                rows={4}
                className="input-field resize-none"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="label">
                  <div className="flex items-center gap-1.5">
                    <Wallet size={14} />
                    预算（可选）
                  </div>
                </label>
                <input
                  type="text"
                  value={budgetRaw}
                  onChange={e => setBudgetRaw(e.target.value)}
                  placeholder="如 5000元"
                  className="input-field"
                />
              </div>
              <div>
                <label className="label">
                  <div className="flex items-center gap-1.5">
                    <Users size={14} />
                    出行人数
                  </div>
                </label>
                <input
                  type="number"
                  value={travelers}
                  onChange={e => setTravelers(Math.max(1, parseInt(e.target.value) || 1))}
                  min={1}
                  max={20}
                  className="input-field"
                />
              </div>
            </div>

            {error && (
              <div className="bg-coral-50 text-coral-700 text-sm px-4 py-2.5 rounded-xl border border-coral-200/50">
                {error}
              </div>
            )}

            <button type="submit" disabled={!prompt.trim() || submitting} className="btn-primary w-full">
              {submitting ? (
                <span className="flex items-center gap-2">
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  提交中...
                </span>
              ) : (
                <span className="flex items-center gap-2">
                  <Send size={16} />
                  开始规划
                </span>
              )}
            </button>
          </form>

          {/* Quick examples */}
          <div className="mt-6">
            <p className="text-xs text-gray-400 mb-3 text-center">快速尝试</p>
            <div className="grid grid-cols-2 gap-2">
              {QUICK_EXAMPLES.map((example, i) => (
                <button
                  key={i}
                  onClick={() => setPrompt(example)}
                  className="text-left text-xs text-gray-400 hover:text-gray-600 bg-white/50 hover:bg-white/80 border border-sand-200/50 rounded-xl px-3 py-2 transition-all duration-200 line-clamp-2"
                >
                  {example}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
