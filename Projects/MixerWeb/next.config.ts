import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  devIndicators: false,
  transpilePackages: ["@mixer/audio-core", "@mixer/domain"]
};

export default nextConfig;
