import type { PlanRequest, PlanResponse, TravelPlanHistory, PageResponse, StatsData } from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1'
const REQUEST_TIMEOUT = 30_000
const MAX_RETRIES = 2

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const isGet = !options?.method || options.method === 'GET'

  const doFetch = async (): Promise<T> => {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT)

    const signal = options?.signal
      ? combineSignals(options.signal, controller.signal)
      : controller.signal

    try {
      const res = await fetch(`${BASE_URL}${url}`, {
        headers: { 'Content-Type': 'application/json' },
        ...options,
        signal,
      })
      if (!res.ok) {
        const text = await res.text().catch(() => 'Unknown error')
        throw new Error(`HTTP ${res.status}: ${text}`)
      }
      return res.json()
    } finally {
      clearTimeout(timeoutId)
    }
  }

  // 非 GET 请求不重试，直接发一次
  if (!isGet) {
    return doFetch()
  }

  // GET 请求：503 时自动重试最多 2 次
  let lastErr: Error | null = null
  for (let i = 0; i <= MAX_RETRIES; i++) {
    try {
      return await doFetch()
    } catch (err) {
      lastErr = err instanceof Error ? err : new Error(String(err))
      if (!lastErr.message.includes('HTTP 503') || i === MAX_RETRIES) {
        throw lastErr
      }
      console.warn(`[API] 请求失败 (${i + 1}/${MAX_RETRIES})，重试...`, url)
      await new Promise(r => setTimeout(r, 1000 * (i + 1)))
    }
  }
  throw lastErr!
}

/** Combine two AbortSignals into one */
function combineSignals(s1: AbortSignal, s2: AbortSignal): AbortSignal {
  if (typeof AbortSignal.any === 'function') {
    return AbortSignal.any([s1, s2])
  }
  // Fallback: use s1 but abort on s2 too
  s2.addEventListener('abort', () => s1.dispatchEvent?.(new Event('abort')))
  return s1
}

export function createPlan(data: PlanRequest): Promise<PlanResponse> {
  return request<PlanResponse>('/plan', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function getPlan(planId: string): Promise<PlanResponse> {
  return request<PlanResponse>(`/plan/${planId}`)
}

export function getHistory(page = 0, size = 10): Promise<PageResponse<TravelPlanHistory>> {
  return request(`/history?page=${page}&size=${size}`)
}

export function getStats(): Promise<StatsData> {
  return request<StatsData>('/stats')
}

export function getStreamUrl(planId: string): string {
  return `${BASE_URL}/plan/${planId}/stream`
}
