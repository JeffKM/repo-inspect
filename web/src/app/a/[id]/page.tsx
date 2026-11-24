import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { AnalysisResult } from "@/components/AnalysisResult";
import { getAnalysis } from "@/lib/api";

type Props = { params: Promise<{ id: string }> };

const ID = /^\d{1,18}$/;

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const a = ID.test(id) ? await getAnalysis(id).catch(() => null) : null;
  return { title: a ? `${a.owner}/${a.name} 권한 명세서` : "분석 결과" };
}

export default async function AnalysisPage({ params }: Props) {
  const { id } = await params;
  if (!ID.test(id)) {
    notFound();
  }
  const a = await getAnalysis(id);
  if (!a) {
    notFound();
  }
  return <AnalysisResult a={a} />;
}
