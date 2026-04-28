import { useState, useRef, useCallback, useEffect } from 'react'
import type { StreamEvent, PlanResponse } from '../types'
import { getPlan } from '../services/api'

interface UsePlanStreamReturn {
  events: StreamEvent[]
  plan: PlanResponse | null
  isComplete: boolean
  error: string | null
  isReconnecting: boolean
  stalled: boolean
  connect: (planId: string) => void
  disconnect: () => void
}

const SSE_TIMEOUT = 15_000   // no data for 15s → stalled warning
const FALLBACK_DELAY = 5_000 // no events in 5s → try direct GET for completed plans
const MAX_RETRIES = 3
const BASE_DELAY = 1_000
const MAX_DELAY = 8_000

export function usePlanStream(): UsePlanStreamReturn {
  const [events, setEvents] = useState<StreamEvent[]>([])
  const [plan, setPlan] = useState<PlanResponse | null>(null)
  const [isComplete, setIsComplete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isReconnecting, setIsReconnecting] = useState(false)
  const [stalled, setStalled] = useState(false)

  const abortRef = useRef<AbortController | null>(null)
  const doneRef = useRef(false)            // prevents stale state updates after terminal state
  const planIdRef = useRef('')
  const retriesRef = useRef(0)
  const stalledTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const fallbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearTimers = useCallback(() => {
    if (stalledTimerRef.current) { clearTimeout(stalledTimerRef.current); stalledTimerRef.current = null }
    if (fallbackTimerRef.current) { clearTimeout(fallbackTimerRef.current); fallbackTimerRef.current = null }
    if (reconnectTimerRef.current) { clearTimeout(reconnectTimerRef.current); reconnectTimerRef.current = null }
  }, [])

  const markDone = useCallback(() => { doneRef.current = true; clearTimers() }, [clearTimers])

  const disconnect = useCallback(() => {
    markDone()
    if (abortRef.current) {
      abortRef.current.abort()
      abortRef.current = null
    }
    setIsReconnecting(false)
    setStalled(false)
    retriesRef.current = 0
  }, [markDone])

  const doFetchPlan = useCallback((planId: string) => {
    getPlan(planId).then(data => {
      if (doneRef.current) return
      setPlan(data)
      setIsComplete(true)
    }).catch(err => {
      if (doneRef.current) return
      setError('获取规划结果失败: ' + (err instanceof Error ? err.message : String(err)))
    })
  }, [])

  const doConnect = useCallback((planId: string, retries = 0) => {
    if (doneRef.current) return

    const controller = new AbortController()
    abortRef.current = controller

    const baseUrl = import.meta.env.VITE_API_BASE || '/api/v1'
    const url = `${baseUrl}/plan/${planId}/stream`

    // Stalled timer: warn if no data arrives within SSE_TIMEOUT
    stalledTimerRef.current = setTimeout(() => {
      if (!doneRef.current) setStalled(true)
    }, SSE_TIMEOUT)

    // Fallback timer: if SSE stream yields nothing within FALLBACK_DELAY,
    // the plan might already be complete — fetch directly
    fallbackTimerRef.current = setTimeout(() => {
      if (!doneRef.current && events.length === 0) {
        doFetchPlan(planId)
        controller.abort()
      }
    }, FALLBACK_DELAY)

    ;(async () => {
      try {
        const res = await fetch(url, {
          headers: { Accept: 'text/event-stream' },
          signal: controller.signal,
        })

        if (!res.ok) {
          if (doneRef.current) return
          // If SSE returns 404/410, the stream is gone → fetch directly
          doFetchPlan(planId)
          return
        }

        const reader = res.body?.getReader()
        if (!reader) {
          if (doneRef.current) return
          doFetchPlan(planId)
          return
        }

        const decoder = new TextDecoder()
        let buffer = ''
        let eventCount = 0

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          // Data received → clear stalled warning, reset retry counter
          setStalled(false)
          retriesRef.current = 0

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          let currentEventType = ''
          let currentData = ''

          for (const line of lines) {
            if (line.startsWith('event: ')) {
              currentEventType = line.slice(7).trim()
            } else if (line.startsWith('data: ')) {
              currentData = line.slice(6).trim()
            } else if (line === '') {
              if (currentData) {
                try {
                  const parsed: StreamEvent = JSON.parse(currentData)
                  eventCount++
                  setEvents(prev => [...prev, parsed])

                  if (parsed.type === 'complete') {
                    markDone()
                    setIsComplete(true)
                    getPlan(planId).then(data => {
                      if (!doneRef.current) setPlan(data)
                    }).catch(console.error)
                    controller.abort()
                    return
                  }

                  if (parsed.type === 'error') {
                    markDone()
                    setError(parsed.content || '规划出错')
                    controller.abort()
                    return
                  }
                } catch {
                  // skip unparseable data
                }
              }
              currentEventType = ''
              currentData = ''
            }
          }
        }

        // Stream closed normally but no complete event → plan may be done
        if (!doneRef.current) {
          doFetchPlan(planId)
        }
      } catch (err) {
        if (doneRef.current) return
        // Only reconnect on transient errors, not backend errors
        const errName = (err as Error)?.name
        if (errName === 'AbortError') return

        if (retries < MAX_RETRIES) {
          const delay = Math.min(BASE_DELAY * Math.pow(2, retries), MAX_DELAY)
          setIsReconnecting(true)
          retriesRef.current = retries + 1
          reconnectTimerRef.current = setTimeout(() => {
            if (!doneRef.current) {
              doConnect(planId, retries + 1)
            }
          }, delay)
        } else {
          setError('SSE 连接中断，请刷新重试')
        }
      }
    })()
  }, [markDone, doFetchPlan, events.length])

  const connect = useCallback((planId: string) => {
    disconnect()
    setEvents([])
    setPlan(null)
    setIsComplete(false)
    setError(null)
    setIsReconnecting(false)
    setStalled(false)
    doneRef.current = false
    planIdRef.current = planId

    doConnect(planId)
  }, [disconnect, doConnect])

  useEffect(() => {
    return () => disconnect()
  }, [disconnect])

  return { events, plan, isComplete, error, isReconnecting, stalled, connect, disconnect }
}
