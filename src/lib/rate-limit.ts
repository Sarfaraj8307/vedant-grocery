/**
 * Minimal in-memory rate limiter for the Next.js proxy.
 * Suitable for single-instance / small-scale; for multi-instance use a Redis/Upstash store.
 */

type Bucket = { count: number; resetAt: number };

const buckets = new Map<string, Bucket>();

function keyFor(ip: string, route: string): string {
  return `${ip}:${route}`;
}

export function rateLimit(
  ip: string,
  route: string,
  limit: number,
  windowMs: number,
): { allowed: boolean; remaining: number; resetAt: number } {
  const key = keyFor(ip, route);
  const now = Date.now();
  const bucket = buckets.get(key);

  if (!bucket || now > bucket.resetAt) {
    const resetAt = now + windowMs;
    buckets.set(key, { count: 1, resetAt });
    return { allowed: true, remaining: limit - 1, resetAt };
  }

  if (bucket.count >= limit) {
    return { allowed: false, remaining: 0, resetAt: bucket.resetAt };
  }

  bucket.count += 1;
  return { allowed: true, remaining: limit - bucket.count, resetAt: bucket.resetAt };
}

export function clientIp(request: Request): string {
  const h = request.headers;
  const xf = h.get("x-forwarded-for");
  if (xf) return xf.split(",")[0]!.trim();
  const xr = h.get("x-real-ip");
  if (xr) return xr.trim();
  return "unknown";
}
