import type { NextConfig } from "next";

const nextConfig: NextConfig = {
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
