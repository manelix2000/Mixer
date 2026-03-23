"use client";

import { useMemo, useState } from "react";

type NewsletterResponse = {
  ok: boolean;
  message: string;
};

export function NewsletterSignup() {
  const [email, setEmail] = useState("");
  const [company, setCompany] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<NewsletterResponse | null>(null);
  const [startedAt] = useState(() => Date.now());

  const statusTone = useMemo(() => {
    if (!result) {
      return "text-[#b8c6dd]";
    }
    return result.ok ? "text-[#9ce2b6]" : "text-[#ffc4b9]";
  }, [result]);

  return (
    <section className="mt-12 rounded-2xl border border-white/14 bg-[linear-gradient(180deg,_rgba(255,255,255,0.1),_rgba(255,255,255,0.04))] p-6 shadow-[0_18px_34px_rgba(0,0,0,0.24)]">
      <h2 className="text-2xl font-semibold tracking-tight md:text-3xl">Sign up for our newsletter</h2>
      <p className="mt-2 max-w-[860px] text-sm leading-relaxed text-[#cad5e7]">
        I would like to read about the latest and greatest on the djay product line by Algoriddim.
        If I should change my mind, I can unsubscribe at any time. Further information can be found
        in the privacy policy.
      </p>

      <form
        className="mt-5 flex flex-col gap-3 md:flex-row md:items-end"
        onSubmit={async (event) => {
          event.preventDefault();
          if (isSubmitting) {
            return;
          }

          setIsSubmitting(true);
          setResult(null);

          try {
            const response = await fetch("/api/newsletter/subscribe", {
              method: "POST",
              headers: {
                "content-type": "application/json"
              },
              body: JSON.stringify({
                email,
                company,
                startedAt
              })
            });

            const payload = (await response.json()) as NewsletterResponse;
            setResult(payload);
            if (response.ok && payload.ok) {
              setEmail("");
              setCompany("");
            }
          } catch {
            setResult({
              ok: false,
              message: "Subscription failed. Please try again."
            });
          } finally {
            setIsSubmitting(false);
          }
        }}
      >
        <label className="flex-1">
          <span className="text-xs font-semibold uppercase tracking-[0.17em] text-[#a9bad6]">Email</span>
          <input
            autoComplete="email"
            className="mt-1.5 h-11 w-full rounded-xl border border-white/18 bg-[#111a27]/75 px-3 text-sm text-[#e7edf8] outline-none transition focus:border-[#83aef7] focus:ring-1 focus:ring-[#83aef7]"
            maxLength={190}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
            required
            type="email"
            value={email}
          />
        </label>

        <div aria-hidden="true" className="hidden">
          <label htmlFor="newsletter-company-field">Company</label>
          <input
            autoComplete="organization"
            id="newsletter-company-field"
            name="company"
            onChange={(event) => setCompany(event.target.value)}
            tabIndex={-1}
            type="text"
            value={company}
          />
        </div>

        <button
          className="inline-flex h-11 items-center justify-center rounded-full border border-[#3d75c7] bg-[linear-gradient(180deg,_#4d8ce8_0%,_#366fc5_100%)] px-5 text-sm font-semibold text-white shadow-[0_8px_16px_rgba(16,45,90,0.45)] disabled:opacity-50"
          disabled={isSubmitting}
          type="submit"
        >
          {isSubmitting ? "Subscribing..." : "Subscribe"}
        </button>
      </form>

      <p className={`mt-3 text-sm ${statusTone}`}>
        {result?.message ?? "Newsletter updates are occasional and focused on product releases."}
      </p>
    </section>
  );
}
