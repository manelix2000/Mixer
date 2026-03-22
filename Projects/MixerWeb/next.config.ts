import { withBotId } from "botid/next/config";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  devIndicators: false,
  transpilePackages: ["@mixer/audio-core", "@mixer/domain"]
};

export default withBotId(nextConfig);
