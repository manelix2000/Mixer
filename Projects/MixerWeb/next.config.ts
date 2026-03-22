import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: ["@mixer/audio-core", "@mixer/domain"]
};

export default nextConfig;
