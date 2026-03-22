# Vercel Deployment Guide (MixerWeb)

This project lives in a monorepo.  
The web app to deploy is in:

- `Projects/MixerWeb`

## 1. Prerequisites

- GitHub repo connected (the same repo that contains iOS + web code).
- A Vercel account: <https://vercel.com>
- `pnpm` is already configured in this project.

---

## 2. Import Project in Vercel

1. In Vercel, click **Add New... > Project**.
2. Select your GitHub repo (`manelix2000/Mixer`).
3. Set these project settings:
   - **Framework Preset**: `Next.js`
   - **Root Directory**: `Projects/MixerWeb`
   - **Install Command**: `pnpm install`
   - **Build Command**: `pnpm build`
   - **Output Directory**: leave default (Next.js)

4. Click **Deploy**.

---

## 3. Auto-Deploy on Every Repo Change (Recommended)

With Vercel Git integration enabled, deployments are automatic:

- Push to `main` -> production deploy
- Push to other branches / PRs -> preview deploy

No webhook is required for this mode.

---

## 4. Direct Deploy Hook (Optional, Explicit Hook URL)

If you want a direct URL hook for deployment triggers:

1. Open your Vercel project.
2. Go to **Settings > Git > Deploy Hooks**.
3. Create a hook (for example):
   - **Name**: `mixerweb-main`
   - **Branch**: `main`
4. Copy the generated hook URL.

Example trigger:

```bash
curl -X POST "https://api.vercel.com/v1/integrations/deploy/<YOUR_HOOK_ID>"
```

You can call this URL from GitHub Actions, CI, or any webhook-capable service.

---

## 5. GitHub Action to Trigger Hook on Every Push (Optional)

Create `.github/workflows/vercel-hook.yml` in repo root:

```yaml
name: Trigger Vercel Deploy Hook

on:
  push:
    branches:
      - main

jobs:
  deploy-hook:
    runs-on: ubuntu-latest
    steps:
      - name: Call Vercel Deploy Hook
        run: curl -X POST "${{ secrets.VERCEL_DEPLOY_HOOK_URL }}"
```

Then add this GitHub secret:

- `VERCEL_DEPLOY_HOOK_URL` = your deploy hook URL from Vercel

---

## 6. Notes for This Monorepo

- Keep iOS code at repo root as-is.
- Vercel must continue using `Projects/MixerWeb` as Root Directory.
- Workspace dependencies are resolved from `Projects/MixerWeb` (`pnpm-workspace.yaml` is already there).

---

## 7. Verification Checklist

After setup:

1. Push a small change to `main`.
2. Confirm Vercel starts a new deployment.
3. Open deployment URL and verify routes:
   - `/` (landing)
   - `/browser` (web mixer app)
   - `/faq`
   - `/contact`
   - `/privacy`

---

## 8. Contact Form (Vercel Function + Anti-Bot)

The contact form uses:

- Next.js Route Handler at `src/app/api/contact/route.ts` (runs as a Vercel Function).
- Vercel BotID (primary anti-bot layer, enforced in client + server).
- Server-side rate limit + honeypot + minimum-submit-time validation.
- Resend Email API for message delivery.

### Vercel BotID Setup (Required)

1. In Vercel dashboard, open your project.
2. Go to **Security** and enable **BotID**.
3. Redeploy after enabling BotID.
4. Confirm these code integrations exist in this repo:
   - `next.config.ts` wraps config with `withBotId(...)`
   - `src/instrumentation-client.ts` protects `POST /api/contact`
   - `src/app/api/contact/route.ts` calls `checkBotId()` before processing form data
5. Ensure your deployment is actually running on Vercel (BotID headers/context are provided by Vercel runtime).

BotID does not require a public site key in the form UI.
It runs as an invisible challenge and sends verification metadata that `checkBotId()` reads server-side.

### Local Development Notes (BotID)

- Local development is allowed to continue even if BotID runtime metadata is unavailable.
- Final bot enforcement happens on Vercel deployments.
- Always validate bot behavior in a preview or production deployment, not only `localhost`.

### BotID Verification Checklist

After deploying:

1. Open `/contact`.
2. Submit a normal form request and confirm success.
3. Check Vercel logs for `/api/contact` if a request is blocked (`403` with bot protection message).
4. Keep server-side fallback checks enabled (rate limit, honeypot, minimum submit time) for layered protection.

### Required Environment Variables (Vercel Project Settings > Environment Variables)

- `CONTACT_TO_EMAIL`  
  Destination inbox (example: `support@yourdomain.com`).
- `CONTACT_FROM_EMAIL`  
  Verified sender in Resend (example: `DJcompanion <noreply@yourdomain.com>`).
- `RESEND_API_KEY`  
  Your Resend API key.

### Resend Setup

1. Create a Resend account.
2. Verify a sending domain or sender identity.
3. Create an API key.
4. Set:
   - `RESEND_API_KEY`
   - `CONTACT_FROM_EMAIL` (must be verified in Resend)
   - `CONTACT_TO_EMAIL` (where form messages are delivered)

### Validation Behavior

- BotID blocks non-verified bot traffic on `/api/contact`.
- Server rejects likely bots using:
  - hidden honeypot field
  - too-fast submit check
  - per-IP rate limit (windowed)
- If mail env vars are missing, `/api/contact` responds with `503` to prevent fake success.
