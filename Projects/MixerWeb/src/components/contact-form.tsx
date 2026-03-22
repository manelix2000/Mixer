"use client";

import { useMemo, useState } from "react";

type ContactResponse = {
  ok: boolean;
  message: string;
};

export function ContactForm() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [company, setCompany] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<ContactResponse | null>(null);
  const [startedAt] = useState(() => Date.now());

  const statusTone = useMemo(() => {
    if (!result) {
      return "text-[#c6d1e3]";
    }
    return result.ok ? "text-[#9ce2b6]" : "text-[#ffc4b9]";
  }, [result]);

  return (
    <form
        className="mt-6 rounded-2xl border border-white/12 bg-[linear-gradient(180deg,_rgba(255,255,255,0.07),_rgba(255,255,255,0.02))] p-6 shadow-[0_20px_32px_rgba(0,0,0,0.24)]"
        onSubmit={async (event) => {
          event.preventDefault();
          if (isSubmitting) {
            return;
          }

          setIsSubmitting(true);
          setResult(null);

          try {
            const response = await fetch("/api/contact", {
              method: "POST",
              headers: {
                "content-type": "application/json"
              },
              body: JSON.stringify({
                name,
                email,
                subject,
                message,
                company,
                startedAt
              })
            });

            const payload = (await response.json()) as ContactResponse;
            setResult(payload);
            if (response.ok && payload.ok) {
              setName("");
              setEmail("");
              setSubject("");
              setMessage("");
              setCompany("");
            }
          } catch {
            setResult({
              ok: false,
              message: "Contact request failed. Please try again in a moment."
            });
          } finally {
            setIsSubmitting(false);
          }
        }}
      >
        <div className="grid gap-4 md:grid-cols-2">
          <Field
            autoComplete="name"
            label="Name"
            maxLength={120}
            onChange={setName}
            placeholder="Your name"
            required
            type="text"
            value={name}
          />
          <Field
            autoComplete="email"
            label="Email"
            maxLength={190}
            onChange={setEmail}
            placeholder="you@example.com"
            required
            type="email"
            value={email}
          />
        </div>

        <div className="mt-4">
          <Field
            autoComplete="off"
            label="Subject"
            maxLength={160}
            onChange={setSubject}
            placeholder="Topic"
            required
            type="text"
            value={subject}
          />
        </div>

        <div className="mt-4">
          <label className="block text-xs font-semibold uppercase tracking-[0.17em] text-[#a9bad6]">
            Message
          </label>
          <textarea
            className="mt-1.5 h-40 w-full rounded-xl border border-white/18 bg-[#111a27]/75 px-3 py-2 text-sm text-[#e7edf8] outline-none transition focus:border-[#83aef7] focus:ring-1 focus:ring-[#83aef7]"
            maxLength={3000}
            onChange={(event) => setMessage(event.target.value)}
            placeholder="How can we help?"
            required
            value={message}
          />
        </div>

        <div aria-hidden="true" className="hidden">
          <label htmlFor="company-field">Company</label>
          <input
            autoComplete="organization"
            id="company-field"
            name="company"
            onChange={(event) => setCompany(event.target.value)}
            tabIndex={-1}
            type="text"
            value={company}
          />
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
          <p className={`text-sm ${statusTone}`}>{result?.message ?? "Send us a message."}</p>
          <button
            className="inline-flex h-10 items-center rounded-full border border-[#3d75c7] bg-[linear-gradient(180deg,_#4d8ce8_0%,_#366fc5_100%)] px-5 text-sm font-semibold text-white shadow-[0_8px_16px_rgba(16,45,90,0.45)] disabled:opacity-50"
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? "Sending..." : "Send Message"}
          </button>
        </div>
      </form>
  );
}

function Field({
  autoComplete,
  label,
  maxLength,
  onChange,
  placeholder,
  required = false,
  type,
  value
}: {
  autoComplete: string;
  label: string;
  maxLength: number;
  onChange: (value: string) => void;
  placeholder: string;
  required?: boolean;
  type: "text" | "email";
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-xs font-semibold uppercase tracking-[0.17em] text-[#a9bad6]">{label}</span>
      <input
        autoComplete={autoComplete}
        className="mt-1.5 h-10 w-full rounded-xl border border-white/18 bg-[#111a27]/75 px-3 text-sm text-[#e7edf8] outline-none transition focus:border-[#83aef7] focus:ring-1 focus:ring-[#83aef7]"
        maxLength={maxLength}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        required={required}
        type={type}
        value={value}
      />
    </label>
  );
}
