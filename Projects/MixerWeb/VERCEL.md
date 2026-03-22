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
   - `/privacy`

