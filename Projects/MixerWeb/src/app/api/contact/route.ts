import { checkBotId } from "botid/server";
import { NextRequest, NextResponse } from "next/server";

type RateLimitState = {
  count: number;
  resetAt: number;
};

type ContactBody = {
  company?: unknown;
  email?: unknown;
  message?: unknown;
  name?: unknown;
  startedAt?: unknown;
  subject?: unknown;
};

const RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
const RATE_LIMIT_MAX = 6;
const MIN_SUBMIT_TIME_MS = 1800;

const globalRateLimitMap = globalThis as typeof globalThis & {
  __contactRateLimitMap?: Map<string, RateLimitState>;
};

function getRateMap() {
  if (!globalRateLimitMap.__contactRateLimitMap) {
    globalRateLimitMap.__contactRateLimitMap = new Map<string, RateLimitState>();
  }
  return globalRateLimitMap.__contactRateLimitMap;
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

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

async function sendWithResend({
  from,
  to,
  subject,
  html,
  replyTo
}: {
  from: string;
  html: string;
  replyTo: string;
  subject: string;
  to: string;
}) {
  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    return { ok: false, reason: "missing_api_key" as const };
  }

  let response: Response;
  try {
    response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        from,
        to,
        subject,
        html,
        reply_to: replyTo
      })
    });
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : "unknown";
    return { ok: false, reason: "network_error" as const, detail: errorMessage };
  }

  if (!response.ok) {
    let detail = "";
    try {
      detail = await response.text();
    } catch {
      detail = "";
    }
    return {
      ok: false,
      reason: "send_failed" as const,
      status: response.status,
      detail
    };
  }

  return { ok: true };
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

  let body: ContactBody;
  try {
    body = (await request.json()) as ContactBody;
  } catch {
    return NextResponse.json({ ok: false, message: "Invalid request body." }, { status: 400 });
  }

  const name = asString(body.name);
  const email = asString(body.email);
  const subject = asString(body.subject);
  const message = asString(body.message);
  const company = asString(body.company);
  const startedAt = Number(body.startedAt);

  if (company.length > 0) {
    return NextResponse.json({ ok: true, message: "Message received." });
  }

  if (!Number.isFinite(startedAt) || now - startedAt < MIN_SUBMIT_TIME_MS) {
    return NextResponse.json({ ok: false, message: "Please wait and submit again." }, { status: 400 });
  }

  if (name.length < 2 || name.length > 120) {
    return NextResponse.json({ ok: false, message: "Name is invalid." }, { status: 400 });
  }
  if (email.length < 5 || email.length > 190 || !email.includes("@")) {
    return NextResponse.json({ ok: false, message: "Email is invalid." }, { status: 400 });
  }
  if (subject.length < 3 || subject.length > 160) {
    return NextResponse.json({ ok: false, message: "Subject is invalid." }, { status: 400 });
  }
  if (message.length < 10 || message.length > 3000) {
    return NextResponse.json({ ok: false, message: "Message is invalid." }, { status: 400 });
  }

  const toEmail = process.env.CONTACT_TO_EMAIL;
  const fromEmail = process.env.CONTACT_FROM_EMAIL;
  if (!toEmail || !fromEmail) {
    return NextResponse.json(
      { ok: false, message: "Contact service is not configured." },
      { status: 503 }
    );
  }

  const safeName = escapeHtml(name);
  const safeEmail = escapeHtml(email);
  const safeSubject = escapeHtml(subject);
  const safeMessage = escapeHtml(message).replaceAll("\n", "<br/>");

  const html = `
    <h2>DJcompanion Contact Form</h2>
    <p><strong>Name:</strong> ${safeName}</p>
    <p><strong>Email:</strong> ${safeEmail}</p>
    <p><strong>Subject:</strong> ${safeSubject}</p>
    <p><strong>Message:</strong><br/>${safeMessage}</p>
    <hr/>
    <p><strong>IP:</strong> ${escapeHtml(ip)}</p>
  `;

  const sent = await sendWithResend({
    from: fromEmail,
    to: toEmail,
    subject: `[DJcompanion] ${subject}`,
    html,
    replyTo: email
  });

  if (!sent.ok) {
    const debugDetail = sent.reason === "send_failed"
      ? `status=${sent.status ?? "unknown"} detail=${sent.detail ?? ""}`
      : sent.reason === "network_error"
        ? `detail=${sent.detail ?? ""}`
        : sent.reason;
    console.error("[contact] resend_send_failed", debugDetail);

    const isProduction = process.env.NODE_ENV === "production";
    const userMessage = isProduction
      ? "Failed to deliver your message. Please try again later."
      : `Failed to deliver your message (${debugDetail}).`;
    return NextResponse.json(
      { ok: false, message: userMessage },
      { status: 502 }
    );
  }

  return NextResponse.json({
    ok: true,
    message: "Message sent successfully. We will get back to you soon."
  });
}
