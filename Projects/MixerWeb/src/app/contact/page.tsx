import type { Metadata } from "next";
import Link from "next/link";
import { ContactForm } from "@/components/contact-form";

export const metadata: Metadata = {
  title: "Contact | DJcompanion",
  description:
    "Contact DJcompanion for product, support, and privacy questions through the protected browser contact form."
};

export default function ContactPage() {
  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top,_#18202d_0%,_#0f141d_45%,_#090d12_100%)] text-[#e9edf5]">
      <section className="mx-auto max-w-[980px] px-6 py-10 md:px-10">
        <header className="rounded-2xl border border-white/14 bg-[linear-gradient(180deg,_rgba(255,255,255,0.1),_rgba(255,255,255,0.03))] p-6 shadow-[0_20px_32px_rgba(0,0,0,0.28)]">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#96bcff]">DJcompanion</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight md:text-4xl">Contact</h1>
          <p className="mt-2 text-sm text-[#c7d2e6]">
            Send support, feature, or privacy questions through this protected form.
          </p>
        </header>

        <ContactForm />

        <div className="mt-6">
          <Link className="text-sm font-semibold text-[#9fc2ff] hover:text-[#bfd6ff]" href="/">
            Back to Home
          </Link>
        </div>
      </section>
    </main>
  );
}
