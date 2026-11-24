import { AnalyzeForm } from "@/components/AnalyzeForm";
import { SITE_DESCRIPTION } from "@/lib/site";

export default function Home() {
  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold">설치하기 전에 까 보세요</h1>
      <p className="text-zinc-600">{SITE_DESCRIPTION}</p>
      <AnalyzeForm />
      <ul className="list-disc space-y-1 pl-5 text-sm text-zinc-600">
        <li>저장소 코드를 실행하지 않고 읽기만 합니다.</li>
        <li>셸 실행, 파일 접근, 외부 전송, 환경 변수·비밀, 설치 스크립트, 난독화 여섯 범주로 나눠 보여 줍니다.</li>
        <li>결과는 발견된 신호입니다. 신호가 없다고 안전하다는 뜻은 아닙니다.</li>
      </ul>
    </section>
  );
}
