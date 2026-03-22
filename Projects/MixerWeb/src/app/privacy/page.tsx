import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Privacy Policy | DJcompanion",
  description:
    "Privacy policy for DJcompanion browser experience, including local audio processing and optional microphone BPM detection."
};

export default function PrivacyPage() {
  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top,_#18202d_0%,_#0f141d_45%,_#090d12_100%)] text-[#e9edf5]">
      <section className="mx-auto max-w-[980px] px-6 py-10 md:px-10">
        <header className="rounded-2xl border border-white/14 bg-[linear-gradient(180deg,_rgba(255,255,255,0.1),_rgba(255,255,255,0.03))] p-6 shadow-[0_20px_32px_rgba(0,0,0,0.28)]">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#96bcff]">DJcompanion</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight md:text-4xl">Privacy Policy</h1>
          <p className="mt-2 text-sm text-[#c7d2e6]">Effective date: March 22, 2026</p>
        </header>

        <article className="mt-6 space-y-5 rounded-2xl border border-white/12 bg-[linear-gradient(180deg,_rgba(255,255,255,0.07),_rgba(255,255,255,0.02))] p-6 text-sm leading-relaxed text-[#cbd5e5]">
          <section>
            <h2 className="text-lg font-semibold text-white">1. Scope</h2>
            <p className="mt-1">
              This policy describes how DJcompanion for Browser handles information when you use the web app.
              DJcompanion is a training-focused DJ companion and does not require account registration to use core features.
            </p>
          </section>

          <section>
            <h2 className="text-lg font-semibold text-white">2. Audio Files You Load</h2>
            <p className="mt-1">
              When you load tracks, files are processed in your browser to enable waveform rendering, BPM estimation, playback, pitch control, and EQ.
              DJcompanion does not intentionally upload your local track files to a DJcompanion backend service.
            </p>
          </section>

          <section>
            <h2 className="text-lg font-semibold text-white">3. Microphone Access</h2>
            <p className="mt-1">
              Microphone BPM listening is optional and only starts after your explicit browser permission.
              Microphone input is used for live BPM detection in-session.
              DJcompanion does not intentionally store microphone recordings on a server.
            </p>
          </section>

          <section>
            <h2 className="text-lg font-semibold text-white">4. Device and Browser Data</h2>
            <p className="mt-1">
              The app may use standard browser/runtime signals needed to provide audio functionality, capability checks, and responsive UI behavior.
              No user account profile is required for normal operation.
            </p>
          </section>

          <section>
            <h2 className="text-lg font-semibold text-white">5. Third-Party Services</h2>
            <p className="mt-1">
              Hosting platforms or network infrastructure may process operational logs according to their own policies.
              Review your deployment provider terms for infrastructure-level processing details.
            </p>
          </section>

          <section>
            <h2 className="text-lg font-semibold text-white">6. Data Retention</h2>
            <p className="mt-1">
              Core DJ training interactions are intended to run locally in-browser for active sessions.
              If you clear browser data, local session data and cached assets may be removed by your browser.
            </p>
          </section>

          <section>
            <h2 className="text-lg font-semibold text-white">7. Your Controls</h2>
            <p className="mt-1">
              You can stop microphone access at any time in-app and revoke permissions in browser settings.
              You control which audio files are loaded during practice sessions.
            </p>
          </section>

          <section>
            <h2 className="text-lg font-semibold text-white">8. Contact</h2>
            <p className="mt-1">
              For privacy-related questions, use the contact form:
              {" "}
              <Link className="text-[#9fc2ff] underline" href="/contact">/contact</Link>.
              The form includes anti-bot protection and rate limiting to reduce abuse.
            </p>
          </section>
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
