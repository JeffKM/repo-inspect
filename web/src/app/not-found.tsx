import Link from "next/link";

export default function NotFound() {
  return (
    <div className="space-y-3">
      <p>분석을 찾을 수 없습니다.</p>
      <Link href="/" className="underline">
        새로 분석하기
      </Link>
    </div>
  );
}
