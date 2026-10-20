# SCHEMA_SNAPSHOT.md — Live column snapshot (machine-checkable)

> Regenerable snapshot of the live Supabase schema. One fenced block per table,
> columns in **stable alphabetical order**, one per line, so a `diff` against a
> fresh fetch is meaningful. Companion to `CONTRACT.md`.

| Field | Value |
|---|---|
| Project ref | `hyibaljxicojfoxbihdn` |
| URL | `https://hyibaljxicojfoxbihdn.supabase.co` |
| Key | `sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH` (publishable — public by design) |
| Fetched (UTC) | `2026-10-11T19:18Z` |
| Postgres | 17.11 |
| Task | Phase 0 · T01 |

## Regeneration command

Run once per table (replace `<table>`); the returned JSON object's keys are the
columns. `200 + row` = anon-visible; `200 + []` = RLS-hidden; `404` = absent.

```bash
URL="https://hyibaljxicojfoxbihdn.supabase.co"
KEY="sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH"
for t in categories products product_weights offers delivery_slots store_settings \
         product_catalog orders order_items order_status_history profiles addresses \
         cart_items complaints complaint_messages complaint_attachments notifications \
         notification_outbox events; do
  echo "=== $t ==="
  curl -s "$URL/rest/v1/$t?select=*&limit=1" \
    -H "apikey: $KEY" -H "Authorization: Bearer $KEY"
  echo
done
```

---

## ANON-VISIBLE TABLES

### categories
```
accent
created_at
glyph
icon
id
image_url
is_active
name
slug
sort_order
updated_at
```

### products
```
accent
availability
base_price
category_id
created_at
description
discount_price
glyph
has_offer
id
image_url
is_active
is_featured
max_order_qty
name
price
quantity_limit
slug
stock
unit
unit_label
updated_at
```

### product_weights
```
created_at
grams
id
is_available
label
price
product_id
updated_at
```

### offers
```
created_at
discount_percent
ends_at
id
is_active
price
product_id
starts_at
title
updated_at
```

### delivery_slots
```
capacity
created_at
end_time
ends_at
id
is_active
max_orders
name
sort_order
start_time
starts_at
updated_at
```

### store_settings
```
delivery_fee
free_delivery_threshold
id
is_open
min_order_amount
phone
store_name
updated_at
whatsapp
```

### product_catalog   (VIEW — read-only)
```
accent
availability
base_price
category_id
category_name
category_slug
description
discount_price
effective_price
glyph
has_offer
id
image_url
is_active
is_featured
max_order_qty
name
price
quantity_limit
slug
stock
unit_label
updated_at
```

---

## RLS-HIDDEN TABLES  —  `anon-visible: none (RLS)`

> These return `[]` to the publishable key. **Columns BLOCKED (no admin creds —
> R1).** Not enumerated; not guessed. Resolve with `docs/probe_authed_columns.sh`
> (T02), then fill in the blocks below and re-run the regeneration command above.

### orders
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### order_items
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### order_status_history
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### profiles
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### addresses
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### cart_items
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### complaints
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### complaint_messages
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### complaint_attachments
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### notifications
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### notification_outbox
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

### events
```
UNKNOWN — BLOCKED (no admin creds — R1) → docs/probe_authed_columns.sh
```

---

*End of SCHEMA_SNAPSHOT.md — 7 anon-visible surfaces enumerated; 12 RLS-hidden
tables pending T02.*
