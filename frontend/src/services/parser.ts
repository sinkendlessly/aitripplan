import type { RouteResult, ItineraryResult, BudgetResult, ParsedPlanData } from '../types'

/**
 * Extract the first JSON object or array from a string.
 * Agent responses often embed JSON between markdown code fences or inside text.
 */
function extractJSON(raw: string): string | null {
  // Try ```json ... ``` block first
  const fenceMatch = raw.match(/```(?:json)?\s*([\s\S]*?)```/)
  if (fenceMatch) return fenceMatch[1].trim()

  // Try {...} block (object)
  const braceMatch = raw.match(/\{[\s\S]*\}/)
  if (braceMatch) return braceMatch[0].trim()

  // Try [...] block (array)
  const bracketMatch = raw.match(/\[[\s\S]*\]/)
  if (bracketMatch) return bracketMatch[0].trim()

  return null
}

function safeParse<T>(raw: string | undefined | null, fallback: T): T {
  if (!raw) return fallback
  const json = extractJSON(raw)
  if (!json) return fallback
  try {
    return JSON.parse(json) as T
  } catch {
    return fallback
  }
}

export function parseRoute(raw: string | undefined | null): RouteResult | undefined {
  const parsed = safeParse<RouteResult | undefined>(raw, undefined)
  return parsed && parsed.origin ? parsed : undefined
}

export function parseItinerary(raw: string | undefined | null): ItineraryResult | undefined {
  const parsed = safeParse<ItineraryResult | undefined>(raw, undefined)
  return parsed && parsed.days ? parsed : undefined
}

export function parseBudget(raw: string | undefined | null): BudgetResult | undefined {
  const parsed = safeParse<BudgetResult | undefined>(raw, undefined)
  return parsed && parsed.total != null ? parsed : undefined
}

export function parsePlanData(
  routeRaw: string | undefined | null,
  itineraryRaw: string | undefined | null,
  budgetRaw: string | undefined | null,
): ParsedPlanData {
  return {
    route: parseRoute(routeRaw),
    itinerary: parseItinerary(itineraryRaw),
    budget: parseBudget(budgetRaw),
  }
}
