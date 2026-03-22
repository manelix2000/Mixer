"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { MixerApp } from "@/components/mixer-app";

const STORE_URL = "https://apps.apple.com/app/id6760940944";

export default function BrowserMixerPage() {
  const [isPhone, setIsPhone] = useState(false);

  useEffect(() => {
    const userAgent = navigator.userAgent.toLowerCase();
    const isTablet = /ipad|tablet|kindle|silk|playbook/.test(userAgent) ||
      (/android/.test(userAgent) && !/mobile/.test(userAgent));
    const isMobilePhone = /iphone|ipod|android.*mobile|windows phone/.test(userAgent);
    const isAndroidPhone = /android/.test(userAgent) && !isTablet;
    const isNarrowPhoneViewport = window.innerWidth <= 820;
    const shouldRedirect = !isTablet && (isMobilePhone || isAndroidPhone) && isNarrowPhoneViewport;

    setIsPhone(shouldRedirect);
  }, []);

  if (isPhone) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-[radial-gradient(circle_at_top,_#18202d_0%,_#0f141d_45%,_#090d12_100%)] px-6 text-white">
        <div className="w-full max-w-[420px] rounded-3xl border border-white/15 bg-white/8 p-7 text-center shadow-[0_24px_44px_rgba(0,0,0,0.34)]">
          <Image
            alt="DJcompanion app icon"
            className="mx-auto h-24 w-24 rounded-[22px] border border-white/25 object-cover shadow-[0_18px_34px_rgba(0,0,0,0.5)]"
            height={96}
            priority
            src="/landing/playstore.png"
            width={96}
          />
          <h1 className="mt-4 text-xl font-semibold tracking-tight">Better Experience on Mobile App</h1>
          <p className="mt-3 text-sm leading-relaxed text-white/80">
            For the best UX on phones, we recommend using DJcompanion in the native app where controls,
            gestures, and audio flow are optimized for mobile performance.
          </p>
          <a
            className="mt-5 inline-flex w-full items-center justify-center rounded-full border border-[#3d75c7] bg-[linear-gradient(180deg,_#4d8ce8_0%,_#366fc5_100%)] px-4 py-2.5 text-sm font-semibold text-white shadow-[0_8px_16px_rgba(16,45,90,0.45)]"
            href={STORE_URL}
          >
            Open Store Page
          </a>
          <Link
            className="mt-3 inline-flex w-full items-center justify-center rounded-full border border-white/25 bg-white/8 px-4 py-2.5 text-sm font-semibold text-white/90"
            href="/"
          >
            Go Back
          </Link>
        </div>
      </main>
    );
  }

  return <MixerApp />;
}
