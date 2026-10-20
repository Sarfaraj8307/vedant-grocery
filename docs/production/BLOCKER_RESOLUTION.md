# Blocker Resolution — step by step

> Three blockers were reported. **One is yours alone** (a credential action), **one I can build**
> (and have), and **one I was wrong about** — reverse geocoding does not require a paid key.

| # | Blocker | Who | Effort | Status after this pass |
|---|---|---|---|---|
| 1 | Rotate the Supabase PAT | **You** | ~3 minutes | Still open — dashboard-only |
| 2 | Multi-staff UI | — | — | **Not needed** — you want one admin only, so the staff screen was removed |
| 3 | Maps for GPS / pin | Me | Built, keyless | **Resolved** (no key needed) |

---

## 1. Rotate the Supabase PAT — your 3 minutes

The token was pasted into a chat and written to disk. It grants full account control, so treat it
as compromised until revoked.

### Step 1 — Revoke the old token

1. Open <https://supabase.com/dashboard/account/tokens>
2. Find the token created around **2026-10-11** (the one used for this work).
3. Click **Revoke**.

> If you cannot tell which one it was: revoke **all** of them. Nothing in this project depends on
> a PAT at runtime — it was only used for migrations and probes. Revoking all of them breaks
> nothing that ships.

### Step 2 — Confirm the old token is dead

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer <OLD_TOKEN>" \
  https://api.supabase.com/v1/projects
# Expected: 401
```

Anything other than `401` means it is still live — revoke again.

### Step 3 — Issue a new token (only if you still need CLI/migration access)

1. Same page → **Generate new token**.
2. Name it something dated and scoped, e.g. `vedant-migrations-2026-10`.
3. Copy it **once** — Supabase shows it only at creation.

### Step 4 — Store it correctly

| Where | Correct? |
|---|---|
| Password manager (1Password/Bitwarden) | ✅ |
| CI secret store (GitHub Actions secrets) | ✅ |
| Shell env var for a one-off session | ✅ |
| `.env.local` | ⚠️ Only if gitignored (it is) |
| Any file in the repo | ❌ |
| Chat, ticket, doc, memory file | ❌ |

### Step 5 — Verify the new token works

```bash
curl -s -H "Authorization: Bearer <NEW_TOKEN>" \
  https://api.supabase.com/v1/projects/<ref> | head -c 120
# Expected: JSON with "status":"ACTIVE_HEALTHY"
```

### What this does *not* affect

Rotating a PAT does **not** change the anon/publishable key, the service-role key, the database
password, or any user session. The live app keeps working throughout.

---

## 2. Multi-staff — removed by request

You asked for **one admin, everyone else a customer**, so the staff screen I had started was
deleted rather than finished: the Compose screen, its view-model, its DTO, the `AdminRpc` methods
and all navigation wiring are gone. `git grep` confirms no residue.

What remains is what the product actually needs:

| Piece | Kept? | Why |
|---|---|---|
| `private.user_roles` + `staff_role` enum | **Kept** | This is how the one admin is identified — it is the authorization source, not a staff feature |
| `owner_grant_role` / `owner_revoke_role` | **Kept** | Still the correct way to create or change the admin account; gated on OWNER with a last-OWNER guard |
| `admin_list_staff()` RPC | Kept but **unused** | Harmless, OWNER-gated. Drop it later if you want the surface minimal |
| Staff screen / navigation | **Removed** | Not wanted |

**To add an admin later**, run this once in the Supabase SQL editor (it goes through the gated RPC):

```sql
select public.owner_grant_role('someone@example.com', 'ADMIN', 'second admin');
```

---

## 3. Maps / GPS — I was wrong; no key is required

I previously said reverse geocoding needs a paid Maps API key. **That was incorrect.** There is a
keyless path that is appropriate for a single-store app, and I have implemented it.

### The keyless stack

| Need | Solution | Cost | Credential |
|---|---|---|---|
| Get coordinates | Browser `navigator.geolocation` | Free | None |
| Turn coordinates into an address | **Nominatim** (OpenStreetMap) reverse geocoding | Free | None |
| Show a map preview | OpenStreetMap embed | Free | None |
| Validate the pin is in Pune | Our own bounding-box check server-side | Free | None |

### Nominatim's rules (this is the only catch)

- **Maximum 1 request per second.** Our flow geocodes once, on an explicit button press — nowhere
  near the limit.
- **A valid identifying `User-Agent` is required.** We set one server-side; a browser request
  without it is blocked by their policy.
- **No bulk geocoding.** We do not batch.
- **No hard SLA.** If Nominatim is down, the customer still types their address manually — the
  feature degrades, it does not break.

### What the customer sees

```
Add address
   ↓
[ Use my current location ]
   ↓  (browser asks permission)
GPS fix: 18.5204, 73.8567   accuracy ±12 m
   ↓
[ Map preview ]  ← OpenStreetMap embed, marker at the pin
   ↓
House / street / area / city / pincode  ← prefilled from reverse geocoding, all editable
   ↓
[ Save address ]
```

### Accuracy handling (§13)

- Accuracy is shown in metres next to the fix.
- If accuracy is worse than **100 m**, we warn: *"Your location accuracy is low — move to an open
  area or adjust the address manually."* and the customer can retry.
- We never silently save a low-accuracy fix as if it were precise — the value is stored alongside
  the coordinates so a dispatcher can see it.

### Permission states handled (§46)

| State | Behaviour |
|---|---|
| Granted | Capture and prefill |
| Denied (this time) | Message + manual entry still available |
| Permanently denied | Explain how to re-enable in browser settings; manual entry always available |
| GPS off / no signal | Timeout message + manual entry |
| Reverse geocoding fails | Coordinates kept, fields left blank for manual entry |

### If you later want a draggable pin

The current picker uses an OSM **embed** (preview + marker, no drag). A draggable pin needs a JS
map library. When you want it:

```bash
pnpm add leaflet react-leaflet
```

then replace the embed with a `MapContainer` + `Marker` whose `draggable` prop updates lat/lng on
`dragend`, and re-run the same reverse-geocode Server Action. Everything else — validation,
accuracy display, manual fallback — stays as built.

### If you would rather pay for Google Maps

Only do this if you need address autocomplete or traffic-aware ETAs:

1. <https://console.cloud.google.com/> → create/select a project.
2. Enable **Maps JavaScript API** and **Geocoding API**.
3. Create an API key, then **restrict it**: HTTP referrers for the web key, and API restrictions to
   just those two APIs.
4. Set a **billing budget alert** — Maps bills per request once the free tier is exhausted.
5. Add the key as `NEXT_PUBLIC_GOOGLE_MAPS_KEY` in `.env.local` **and** your host's env settings.
6. Never put a Maps key with an unrestricted scope in the repo; a `NEXT_PUBLIC_` key is readable by
   anyone who opens the site, which is why the referrer restriction is mandatory.

---

## Verification after all three

```bash
# 1. Old PAT dead
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer <OLD>" \
  https://api.supabase.com/v1/projects          # → 401

# 2. Multi-staff: OWNER can grant, ADMIN cannot
#    (run in the SQL editor, impersonating each identity)
select public.owner_grant_role('someone@example.com','DELIVERY','probe');

# 3. GPS: add an address in the browser, confirm lat/lng persisted
select id, label, city, latitude, longitude from public.addresses order by created_at desc limit 1;
```

## Remaining, honestly

| Item | State |
|---|---|
| PAT rotation | **Needs you** — the only truly human step left |
| Release signing (AAB) | Not done — needs a keystore you must create and keep secret |
| Backup / restore procedure | Not documented |
| Error tracking / crash reporting | Not installed |
| 33-step E2E | Needs a real staff account (fixtures were deleted deliberately) |
