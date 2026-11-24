import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 개발 서버가 AI 도구용 안내 파일(AGENTS.md)을 자동으로 만들지 않게
  agentRules: false,
  // 결과는 요청 시점 데이터라 컴포넌트 캐시를 쓰지 않는다(켜면 결과 페이지의 404 와 no-store 조회가 막힌다)
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
