# Mintline API-Football proxy

A tiny Cloudflare Worker that lets the app show Arab-world and African leagues
from [API-Football](https://www.api-football.com/) without putting the API key
inside the app.

- The key lives only in Cloudflare as a secret (`APIFOOTBALL_KEY`).
- Only three read-only routes exist: `/v1/fixtures`, `/v1/fixture`, `/v1/standings`.
- Every response is cached at Cloudflare's edge and shared by all users, so the
  free API-Football plan (~100 requests/day) is enough: "today" refreshes every
  15 minutes, other days every 6 hours, finished matches are kept for 24 hours.
- Only leagues from Arab and African countries are returned (Saudi Arabia, South
  Africa and Nigeria are left to ESPN, which already covers them).

## Deploy (about 5 minutes)

1. Create a free account at <https://dashboard.api-football.com/register> and copy your API key.
2. Create a free Cloudflare account at <https://dash.cloudflare.com/sign-up>.
3. In this folder:

   ```bash
   cd proxy
   npx wrangler login
   npx wrangler secret put APIFOOTBALL_KEY   # paste the key when asked
   npx wrangler deploy
   ```

   Wrangler prints the Worker URL, e.g. `https://mintline-proxy.<you>.workers.dev`.
4. Check it: open `https://mintline-proxy.<you>.workers.dev/v1/fixtures?date=2026-11-16`.
5. Tell the app about it — add this line to `local.properties` in the project root
   (it is git-ignored):

   ```properties
   mintline.proxyUrl=https://mintline-proxy.<you>.workers.dev
   ```

   then build a release as usual. Leagues such as Botola Pro, the Egyptian Premier
   League, Algerian/Tunisian Ligue 1, Qatar Stars League and UAE Pro League appear
   in the Matches feed alongside ESPN data.

## Limits

If the daily quota runs out, the Worker returns an error (not cached) and the app
simply shows ESPN data until the next day. For faster live updates, lower the
TTLs in `src/index.js` and move to a paid API-Football plan.
