import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "FAQ | DJcompanion",
  description:
    "Frequently asked questions about DJcompanion training workflows, browser usage, microphone BPM, and feature scope."
};

const faqItems = [
  {
    q: "Can I use DJcompanion for a live club set?",
    a: "No. DJcompanion is intentionally focused on learning and practice, not pro live performance workflows."
  },
  {
    q: "Do I need DJ hardware to start?",
    a: "No. You can begin with local audio files, deck controls, waveform tools, and BPM training directly in the app."
  },
  {
    q: "How does microphone BPM work?",
    a: "Enable the mic button and grant browser permission. DJcompanion listens in real time and estimates tempo for training feedback."
  },
  {
    q: "Can I load my own tracks?",
    a: "Yes. The browser workflow supports local audio import so you can practice with your own music."
  },
  {
    q: "Is the browser EQ visual only?",
    a: "No. LOW, MID, and HIGH sliders are wired to a real three-band Web Audio equalizer per deck."
  },
  {
    q: "Is my audio uploaded to DJcompanion servers?",
    a: "Core playback, waveform analysis, and controls are designed to run in-browser for local practice sessions."
  }
];

export default function FaqPage() {
  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top,_#18202d_0%,_#0f141d_45%,_#090d12_100%)] text-[#e9edf5]">
      <section className="mx-auto max-w-[980px] px-6 py-10 md:px-10">
        <header className="rounded-2xl border border-white/14 bg-[linear-gradient(180deg,_rgba(255,255,255,0.1),_rgba(255,255,255,0.03))] p-6 shadow-[0_20px_32px_rgba(0,0,0,0.28)]">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#96bcff]">DJcompanion</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight md:text-4xl">Frequently Asked Questions</h1>
          <p className="mt-2 text-sm text-[#c7d2e6]">
            Common questions about setup, scope, and browser behavior.
          </p>
        </header>

        <article className="mt-6 grid gap-3 rounded-2xl border border-white/12 bg-[linear-gradient(180deg,_rgba(255,255,255,0.07),_rgba(255,255,255,0.02))] p-6">
          {faqItems.map((item) => (
            <section
              key={item.q}
              className="rounded-xl border border-white/10 bg-[linear-gradient(180deg,_rgba(255,255,255,0.06),_rgba(255,255,255,0.02))] p-4"
            >
              <h2 className="text-base font-semibold text-white">{item.q}</h2>
              <p className="mt-2 text-sm leading-relaxed text-[#c8d3e5]">{item.a}</p>
            </section>
          ))}
        </article>

        <div className="mt-6">
          <Link className="text-sm font-semibold text-[#9fc2ff] hover:text-[#bfd6ff]" href="/">
            Back to Home
          </Link>
        </div>
      </section>
    </main>
  );
}
