import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        background: "#081116",
        panel: "#0f1b21",
        panelAlt: "#15262f",
        accent: "#f97316",
        accentSoft: "#fb923c",
        line: "#28414c",
        text: "#edf4f7",
        textMuted: "#8ea6b2"
      },
      boxShadow: {
        platter: "0 24px 80px rgba(0, 0, 0, 0.45)",
        panel: "0 18px 45px rgba(0, 0, 0, 0.3)"
      },
      backgroundImage: {
        mesh: "radial-gradient(circle at top left, rgba(249, 115, 22, 0.22), transparent 30%), radial-gradient(circle at bottom right, rgba(56, 189, 248, 0.16), transparent 34%)"
      }
    }
  },
  plugins: []
};

export default config;
