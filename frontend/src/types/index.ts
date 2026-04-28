// ========== Request types ==========

export interface PlanRequest {
  prompt: string
  options?: {
    budget?: number
    travelers?: number
    travelMode?: string
    preferences?: string[]
  }
}

// ========== Response types (match backend DTO exactly) ==========

export type PlanStatus = 'PROCESSING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'

export interface PlanResponse {
  planId: string
  sessionId: string
  status: PlanStatus
  streamUrl: string
  createdAt: string
  userRequest?: string
  routeResult?: string    // raw JSON string from agent
  itineraryResult?: string  // raw JSON string from agent
  budgetResult?: string   // raw JSON string from agent
  executionTime?: number
  errorMessage?: string
}

// ========== Parsed result types (for display after JSON extraction) ==========

export interface RouteResult {
  origin: string
  destination: string
  totalDistanceKm: number
  segments: RouteSegment[]
  transportMode: string
}

export interface RouteSegment {
  from: string
  to: string
  distanceKm: number
  durationMin: number
  transportMode: string
  description: string
}

export interface ItineraryResult {
  totalDays: number
  days: DayPlan[]
}

export interface DayPlan {
  day: number
  date?: string
  theme?: string
  morning: Activity[]
  afternoon: Activity[]
  evening: Activity[]
}

export interface Activity {
  title: string
  description: string
  duration?: string
  location?: string
  tips?: string
}

export interface BudgetResult {
  total: number
  currency: string
  breakdown: BudgetCategory[]
  tiers: BudgetTier[]
  optimizationTips: string[]
}

export interface BudgetCategory {
  category: string
  amount: number
  percentage: number
}

export interface BudgetTier {
  name: string
  total: number
  description: string
}

// ========== SSE event types (match backend StreamEvent) ==========

export type StreamEventType =
  | 'thinking'
  | 'agent_start'
  | 'route_progress'
  | 'itinerary_progress'
  | 'budget_progress'
  | 'complete'
  | 'error'

export interface StreamEvent {
  type: StreamEventType
  agent?: string
  content?: string
  planId?: string
  totalTime?: number
}

// ========== History (match TravelPlanHistory entity) ==========

export interface TravelPlanHistory {
  id?: number
  planId: string
  sessionId?: string
  userId?: string
  userRequest: string     // the original prompt
  origin?: string
  destination?: string
  days?: number
  status: string
  routeResult?: string
  itineraryResult?: string
  budgetResult?: string
  totalCost?: number
  executionTime?: number
  errorMessage?: string
  fromCache?: boolean
  createTime: string
  updateTime?: string
}

// Paginated response from Spring Page
export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// ========== Stats (match backend format) ==========

export interface StatsData {
  totalPlans: number
  successRate: number
  averageCost: number
  hotDestinations: Array<[string, number]>  // backend sends List<Object[]> = array of tuples
}

// ========== Parsed plan data (frontend-only, derived from raw strings) ==========

export interface ParsedPlanData {
  route?: RouteResult
  itinerary?: ItineraryResult
  budget?: BudgetResult
}
