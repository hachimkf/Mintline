/**
 * Mintline API-Football proxy (Cloudflare Worker).
 *
 * - Keeps the API-Football key secret (Worker secret APIFOOTBALL_KEY), never in the app.
 * - Exposes only three read-only routes the app needs.
 * - Caches every response at Cloudflare's edge so all app users share one
 *   upstream request per cache window — the free plan allows ~100 requests/day.
 * - Only returns leagues from the Arab world and Africa that ESPN doesn't cover.
 *
 * Routes:
 *   GET /v1/fixtures?date=YYYY-MM-DD   fixtures for one day (filtered by country)
 *   GET /v1/fixture?id=123             one fixture with events, lineups, statistics
 *   GET /v1/standings?league=200&season=2026
 */

const UPSTREAM = "https://v3.football.api-sports.io";

// Countries whose leagues Mintline takes from API-Football.
// Saudi Arabia, South Africa and Nigeria are left out: ESPN already covers them.
const COUNTRIES = new Set([
  "Morocco", "Egypt", "Algeria", "Tunisia", "Libya", "Sudan", "Mauritania",
  "Qatar", "United-Arab-Emirates", "Kuwait", "Bahrain", "Oman", "Iraq", "Jordan",
  "Lebanon", "Syria", "Palestine", "Yemen",
  "Ghana", "Senegal", "Ivory-Coast", "Cameroon", "Kenya", "Tanzania", "Uganda",
  "Ethiopia", "Zambia", "Zimbabwe", "Angola", "Mali", "Burkina-Faso", "Congo-DR",
  "Rwanda", "Benin", "Togo", "Guinea", "Gabon", "Botswana", "Namibia", "Mozambique",
  "World", // Arab Cup, Arab Club Champions Cup etc. (filtered by name below)
]);
const WORLD_NAME_HINTS = ["arab", "caf ", "african", "afcon", "cosafa", "cecafa", "wafu", "gulf"];

// Edge cache lifetimes (seconds)
const TTL = {
  fixturesToday: 15 * 60,     // ~96 upstream calls/day at most for "today"
  fixturesOther: 6 * 60 * 60, // past/future days change rarely
  fixtureLive: 3 * 60,
  fixtureDone: 24 * 60 * 60,
  standings: 6 * 60 * 60,
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method !== "GET") return json({ error: "method not allowed" }, 405);
    if (!env.APIFOOTBALL_KEY) return json({ error: "proxy not configured" }, 500);

    // Serve from edge cache first. The key keeps only the allowed parameters, so
    // junk query strings can't bypass the cache and burn the daily API quota.
    const cache = caches.default;
    const allowed = ["date", "id", "league", "season"];
    const canonical = new URL(url.origin + url.pathname);
    for (const k of allowed) if (url.searchParams.has(k)) canonical.searchParams.set(k, url.searchParams.get(k));
    const cacheKey = new Request(canonical.toString(), { method: "GET" });
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    let upstreamPath, ttl, transform;
    try {
      if (url.pathname === "/v1/fixtures") {
        const date = url.searchParams.get("date") || "";
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return json({ error: "bad date" }, 400);
        upstreamPath = `/fixtures?date=${date}`;
        const today = new Date().toISOString().slice(0, 10);
        ttl = date === today ? TTL.fixturesToday : TTL.fixturesOther;
        transform = (data) => ({
          fixtures: (data.response || []).filter((f) => keepLeague(f.league)),
        });
      } else if (url.pathname === "/v1/fixture") {
        const id = url.searchParams.get("id") || "";
        if (!/^\d+$/.test(id)) return json({ error: "bad id" }, 400);
        upstreamPath = `/fixtures?id=${id}`;
        transform = (data) => {
          const f = (data.response || [])[0] || null;
          const done = f && ["FT", "AET", "PEN", "CANC", "PST", "ABD", "AWD", "WO"].includes(f.fixture?.status?.short);
          ttl = done ? TTL.fixtureDone : TTL.fixtureLive;
          return { fixture: f };
        };
        ttl = TTL.fixtureLive;
      } else if (url.pathname === "/v1/standings") {
        const league = url.searchParams.get("league") || "";
        const season = url.searchParams.get("season") || "";
        if (!/^\d+$/.test(league) || !/^\d{4}$/.test(season)) return json({ error: "bad params" }, 400);
        upstreamPath = `/standings?league=${league}&season=${season}`;
        ttl = TTL.standings;
        transform = (data) => ({ standings: data.response?.[0]?.league?.standings || [] });
      } else {
        return json({ error: "not found" }, 404);
      }

      const upstream = await fetch(UPSTREAM + upstreamPath, {
        headers: { "x-apisports-key": env.APIFOOTBALL_KEY },
      });
      if (!upstream.ok) return json({ error: `upstream ${upstream.status}` }, 502);
      const data = await upstream.json();
      if (data.errors && Object.keys(data.errors).length) {
        // e.g. daily limit reached — don't cache errors
        return json({ error: "upstream error", details: data.errors }, 502);
      }
      const body = transform(data);
      const response = json(body, 200, ttl);
      ctx.waitUntil(cache.put(cacheKey, response.clone()));
      return response;
    } catch (e) {
      return json({ error: String(e) }, 500);
    }
  },
};

function keepLeague(league) {
  if (!league) return false;
  if (!COUNTRIES.has(league.country)) return false;
  if (league.country !== "World") return true;
  const n = (league.name || "").toLowerCase();
  return WORLD_NAME_HINTS.some((h) => n.includes(h));
}

function json(body, status = 200, ttl = 0) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": ttl > 0 ? `public, max-age=${Math.min(ttl, 300)}, s-maxage=${ttl}` : "no-store",
    },
  });
}
