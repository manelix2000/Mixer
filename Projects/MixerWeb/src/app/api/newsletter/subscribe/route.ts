import { sql } from "@vercel/postgres";
import { checkBotId } from "botid/server";
import { NextRequest, NextResponse } from "next/server";

type NewsletterBody = {
  company?: unknown;
  email?: unknown;
  startedAt?: unknown;
};

type RateLimitState = {
  count: number;
  resetAt: number;
};

const RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
const RATE_LIMIT_MAX = 8;
const MIN_SUBMIT_TIME_MS = 1200;

const globalRateLimitMap = globalThis as typeof globalThis & {
  __newsletterRateLimitMap?: Map<string, RateLimitState>;
};

function getRateMap() {
  if (!globalRateLimitMap.__newsletterRateLimitMap) {
    globalRateLimitMap.__newsletterRateLimitMap = new Map<string, RateLimitState>();
  }
  return globalRateLimitMap.__newsletterRateLimitMap;
}

function asString(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

function getClientIp(request: NextRequest) {
  const forwarded = request.headers.get("x-forwarded-for");
  if (forwarded) {
    return forwarded.split(",")[0]?.trim() || "unknown";
  }
  return request.headers.get("x-real-ip") ?? "unknown";
}

function isLikelyValidEmail(email: string) {
  if (email.length < 5 || email.length > 190) {
    return false;
  }
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

async function ensureNewsletterTable() {
  await sql`
    CREATE TABLE IF NOT EXISTS newsletter_subscriptions (
      id BIGSERIAL PRIMARY KEY,
      email TEXT NOT NULL UNIQUE,
      status TEXT NOT NULL DEFAULT 'subscribed',
      source TEXT NOT NULL DEFAULT 'website',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `;
}

export async function POST(request: NextRequest) {
  try {
    const botResult = await checkBotId();
    if (botResult.isBot && !botResult.isVerifiedBot) {
      return NextResponse.json(
        { ok: false, message: "Request blocked by bot protection." },
        { status: 403 }
      );
    }
  } catch {
    // Allow local/dev fallback when BotID runtime metadata is unavailable.
  }

  const now = Date.now();
  const ip = getClientIp(request);
  const rateMap = getRateMap();
  const current = rateMap.get(ip);
  if (!current || current.resetAt <= now) {
    rateMap.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS });
  } else {
    if (current.count >= RATE_LIMIT_MAX) {
      return NextResponse.json(
        { ok: false, message: "Too many requests. Try again later." },
        { status: 429 }
      );
    }
    current.count += 1;
    rateMap.set(ip, current);
  }

  let body: NewsletterBody;
  try {
    body = (await request.json()) as NewsletterBody;
  } catch {
    return NextResponse.json({ ok: false, message: "Invalid request body." }, { status: 400 });
  }

  const email = asString(body.email).toLowerCase();
  const company = asString(body.company);
  const startedAt = Number(body.startedAt);

  if (company.length > 0) {
    return NextResponse.json({
      ok: true,
      message: "Subscription confirmed."
    });
  }

  if (!Number.isFinite(startedAt) || now - startedAt < MIN_SUBMIT_TIME_MS) {
    return NextResponse.json({ ok: false, message: "Please wait and submit again." }, { status: 400 });
  }

  if (!isLikelyValidEmail(email)) {
    return NextResponse.json({ ok: false, message: "Email is invalid." }, { status: 400 });
  }

  try {
    await ensureNewsletterTable();

    await sql`
      INSERT INTO newsletter_subscriptions (email, status, source)
      VALUES (${email}, 'subscribed', 'website')
      ON CONFLICT (email)
      DO UPDATE SET
        status = 'subscribed',
        updated_at = NOW()
    `;
  } catch (error) {
    const detail = error instanceof Error ? error.message : "unknown";
    console.error("[newsletter] upsert_failed", detail);
    return NextResponse.json(
      { ok: false, message: "Unable to save your subscription right now." },
      { status: 502 }
    );
  }

  return NextResponse.json({
    ok: true,
    message: "Subscribed successfully. You will receive future updates."
  });
}
