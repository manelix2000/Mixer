import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";

export const metadata: Metadata = {
  title: "DJcompanion | Beat Matching Trainer for Browser, iOS and Android",
  description:
    "DJcompanion helps amateur DJs train beat matching, timing, and BPM control with two-deck practice workflows, waveform feedback, pitch training, and microphone BPM listening.",
  keywords: [
    "DJ beat matching trainer",
    "BPM training app",
    "two deck DJ practice",
    "waveform tempo trainer",
    "DJ companion iOS",
    "DJ companion Android",
    "DJ companion browser"
  ]
};

type ImageFeature = {
  type: "image";
  title: string;
  body: string;
  imageSrc: string;
  imageAlt: string;
  imageClassName: string;
};

type VisualFeature = {
  type: "visual";
  title: string;
  body: string;
  visual: "mic";
};

const featureCards: Array<ImageFeature | VisualFeature> = [
  {
    type: "image",
    title: "Two-Deck Beat Matching Practice",
    body:
      "Train timing with one or two decks, transport controls, and mirrored deck interactions designed for repeated ear-training sessions.",
    imageSrc: "/landing/two-decks.png",
    imageAlt: "DJcompanion two-deck layout",
    imageClassName: "object-cover"
  },
  {
    type: "image",
    title: "Waveform Seek, Drag and Zoom",
    body:
      "Use waveform taps, directional drags, and zoom controls to study phrasing and tighten cue placement while keeping tempo awareness.",
    imageSrc: "/landing/load-track.png",
    imageAlt: "DJcompanion waveform and track loading controls",
    imageClassName: "object-cover"
  },
  {
    type: "image",
    title: "Pitch Training with Sensitivity Steps",
    body:
      "Practice pitch control using vertical faders, temporary pressure tweaks on platter sides, and sensitivity ranges (±2, ±4, ±8, ±16).",
    imageSrc: "/landing/master-controls.png",
    imageAlt: "DJcompanion pitch and transport controls",
    imageClassName: "object-cover"
  },
  {
    type: "image",
    title: "Per-Deck Equalizer",
    body:
      "Shape each deck with LOW, MID, and HIGH controls. The browser version uses a real three-band Web Audio equalizer in the output chain.",
    imageSrc: "/landing/eq-controls.png",
    imageAlt: "DJcompanion equalizer overlay",
    imageClassName: "object-cover"
  },
  {
    type: "visual",
    title: "Microphone BPM Listening",
    body:
      "Enable microphone BPM monitoring to compare external tempo against your deck and practice locking playback rate by ear and feedback.",
    visual: "mic"
  },
  {
    type: "image",
    title: "Local Track Import Workflow",
    body:
      "Load local audio files for practice sessions, inspect waveform behavior, and run BPM estimation directly in-app without external uploads.",
    imageSrc: "/landing/single-deck.png",
    imageAlt: "DJcompanion single deck",
    imageClassName: "object-cover"
  }
];

export default function LandingPage() {
  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top,_#18202d_0%,_#0f141d_45%,_#090d12_100%)] text-[#e9edf5]">
      <section className="mx-auto max-w-[1240px] px-6 pb-16 pt-10 md:px-10">
        <header className="rounded-[30px] border border-white/15 bg-[linear-gradient(135deg,_rgba(255,255,255,0.09)_0%,_rgba(255,255,255,0.05)_48%,_rgba(255,255,255,0.03)_100%)] p-8 shadow-[0_30px_60px_rgba(0,0,0,0.32)] md:p-12">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#8ab6ff]">
            DJcompanion
          </p>
          <div className="mt-4 flex items-center gap-4">
            <Image
              alt="DJcompanion app icon"
              className="h-[56px] w-[56px] rounded-[14px] border border-white/30 object-cover shadow-[0_10px_16px_rgba(0,0,0,0.35)] md:h-[68px] md:w-[68px]"
              height={68}
              src="/landing/playstore.png"
              width={68}
            />
            <h1 className="max-w-[820px] text-4xl font-semibold leading-tight tracking-tight md:text-6xl">
              Practice Beat Matching with a Formal Training Flow
            </h1>
          </div>
          <p className="mt-5 max-w-[760px] text-base text-[#c7d2e5] md:text-lg">
            A fresh and focused DJ learning companion for amateur deejays. Build ear confidence,
            improve timing, and train BPM control across Browser, iOS, and Android tracks.
          </p>
          <div className="mt-8 grid gap-4 md:grid-cols-3">
            <PlatformCard
              badge="Web"
              body="Run DJcompanion in your browser with two-deck training, waveform controls, pitch drills, and real EQ."
              ctaLabel="DJcompanion for Browser"
              href="/browser"
              title="Desktop"
            />
            <PlatformCard
              badge="Mobile"
              body="Native iOS build with turntable workflow, BPM tools, and touch-optimized practice controls."
              ctaLabel="App Store"
              href="https://apps.apple.com/app/id6760940944"
              title="iOS"
            />
            <PlatformCard
              badge="Mobile"
              body="Android access is planned for the same learning-first workflow and feature parity trajectory."
              ctaLabel="Android Status"
              href="https://github.com/manelix2000/Mixer"
              title="Android"
            />
          </div>
        </header>

        <section className="mt-10">
          <div className="rounded-2xl border border-white/14 bg-[linear-gradient(180deg,_rgba(255,255,255,0.09),_rgba(255,255,255,0.03))] p-6 shadow-[0_16px_30px_rgba(0,0,0,0.22)]">
            <h2 className="text-2xl font-semibold tracking-tight md:text-3xl">
              What DJcompanion Is / Is Not
            </h2>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <article className="rounded-xl border border-[#7ea7ff]/45 bg-[#1b2840]/55 p-4">
                <h3 className="text-base font-semibold text-[#b8d1ff]">What It Is</h3>
                <p className="mt-2 text-sm leading-relaxed text-[#d7e2f5]">
                  DJcompanion is a focused training app for beginners and hobbyists who want to
                  build real DJ instincts: beat matching, tempo awareness, cue timing, pitch control,
                  and EQ sensitivity in a clear learning workflow.
                </p>
              </article>
              <article className="rounded-xl border border-[#ffb39f]/45 bg-[#3a2320]/50 p-4">
                <h3 className="text-base font-semibold text-[#ffd0c3]">What It Is Not</h3>
                <p className="mt-2 text-sm leading-relaxed text-[#f1d6cf]">
                  DJcompanion is intentionally not a professional performance platform. It is not
                  built for live club sets, advanced pro workflows, or production-grade show output.
                  It is designed for learning and practice first.
                </p>
              </article>
            </div>
          </div>

          <h2 className="mt-10 text-2xl font-semibold tracking-tight md:text-3xl">
            Core Training Features
          </h2>
          <p className="mt-3 max-w-[760px] text-[#bac7dd]">
            Feature set oriented to practice sessions, not live performance complexity. Every module
            is focused on repetition, timing awareness, and BPM alignment habits.
          </p>
          <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {featureCards.map((feature) => (
              <article
                key={feature.title}
                className="overflow-hidden rounded-2xl border border-white/14 bg-[linear-gradient(180deg,_rgba(255,255,255,0.08)_0%,_rgba(255,255,255,0.03)_100%)] shadow-[0_18px_34px_rgba(0,0,0,0.26)]"
              >
                {feature.type === "image" ? (
                  <div className="relative h-[190px] w-full border-b border-white/10">
                    <Image
                      alt={feature.imageAlt}
                      className={feature.imageClassName}
                      fill
                      sizes="(max-width: 768px) 100vw, 33vw"
                      src={feature.imageSrc}
                    />
                  </div>
                ) : (
                  <FeatureVisual kind={feature.visual} />
                )}
                <div className="p-5">
                  <h3 className="text-lg font-semibold tracking-tight">{feature.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-[#c4cfdf]">{feature.body}</p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <footer className="mt-12 flex flex-wrap items-center justify-between gap-3 border-t border-white/12 pt-6 text-sm text-[#afbdd4]">
          <p>© 2026 DJcompanion</p>
          <div className="flex flex-col items-end gap-1">
            <Link className="text-[#8fb9ff] hover:text-[#b0ceff]" href="/faq">
              FAQ
            </Link>
            <Link className="text-[#8fb9ff] hover:text-[#b0ceff]" href="/privacy">
              Privacy Policy
            </Link>
          </div>
        </footer>
      </section>
    </main>
  );
}

function PlatformCard({
  badge,
  body,
  ctaLabel,
  href,
  title
}: {
  badge: string;
  body: string;
  ctaLabel: string;
  href: string;
  title: string;
}) {
  return (
    <article className="rounded-2xl border border-white/14 bg-[linear-gradient(180deg,_rgba(255,255,255,0.1)_0%,_rgba(255,255,255,0.03)_100%)] p-5 shadow-[0_14px_30px_rgba(0,0,0,0.25)]">
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#9fc2ff]">{badge}</p>
      <h3 className="mt-2 text-2xl font-semibold tracking-tight">{title}</h3>
      <p className="mt-2 min-h-[70px] text-sm leading-relaxed text-[#ccd5e4]">{body}</p>
      <Link
        className="mt-4 inline-flex rounded-full border border-[#3d75c7] bg-[linear-gradient(180deg,_#4d8ce8_0%,_#366fc5_100%)] px-4 py-2 text-sm font-semibold text-white shadow-[0_8px_16px_rgba(16,45,90,0.45)]"
        href={href}
      >
        {ctaLabel}
      </Link>
    </article>
  );
}

function FeatureVisual({ kind }: { kind: "mic" }) {
  if (kind === "mic") {
    return (
      <div className="relative h-[190px] border-b border-white/10 bg-[radial-gradient(circle_at_50%_38%,_#2a364a_0%,_#1c2737_60%,_#131b27_100%)]">
        <div className="absolute left-1/2 top-1/2 h-[96px] w-[96px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[radial-gradient(circle,_#2f4e7f_0%,_#1f3350_56%,_#172438_100%)] shadow-[0_18px_36px_rgba(0,0,0,0.35)]" />
        <div className="absolute left-1/2 top-[39%] h-[58px] w-[34px] -translate-x-1/2 rounded-[22px] border-2 border-[#7fb0ff] bg-[#d9e8ff]/25" />
        <div className="absolute left-1/2 top-[63%] h-[18px] w-[56px] -translate-x-1/2 rounded-full border border-[#7fb0ff]/40 bg-[#d9e8ff]/25" />
        <div className="absolute left-1/2 top-[72%] h-[8px] w-[4px] -translate-x-1/2 rounded-full bg-[#84b4ff]" />
      </div>
    );
  }

  return null;
}
