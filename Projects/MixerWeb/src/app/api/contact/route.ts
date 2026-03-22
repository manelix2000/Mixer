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
  turnstileToken?: unknown;
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

async function verifyTurnstileToken(token: string, ip: string) {
  const secret = process.env.TURNSTILE_SECRET_KEY;
  if (!secret) {
    return { ok: true };
  }

  const form = new URLSearchParams();
  form.set("secret", secret);
  form.set("response", token);
  form.set("remoteip", ip);

  const response = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
    method: "POST",
    body: form
  });

  if (!response.ok) {
    return { ok: false };
  }

  const payload = (await response.json()) as { success?: boolean };
  return { ok: payload.success === true };
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

  const response = await fetch("https://api.resend.com/emails", {
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

  if (!response.ok) {
    return { ok: false, reason: "send_failed" as const };
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
  const turnstileToken = asString(body.turnstileToken);
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

  if (process.env.TURNSTILE_SECRET_KEY) {
    if (!turnstileToken) {
      return NextResponse.json(
        { ok: false, message: "Anti-bot challenge is required." },
        { status: 400 }
      );
    }
    const turnstile = await verifyTurnstileToken(turnstileToken, ip);
    if (!turnstile.ok) {
      return NextResponse.json(
        { ok: false, message: "Anti-bot verification failed." },
        { status: 400 }
      );
    }
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
    return NextResponse.json(
      { ok: false, message: "Failed to deliver your message. Please try again later." },
      { status: 502 }
    );
  }

  return NextResponse.json({
    ok: true,
    message: "Message sent successfully. We will get back to you soon."
  });
}
