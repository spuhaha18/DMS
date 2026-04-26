"use client";
import { useQuery } from "@tanstack/react-query";
import { useRouter, useParams } from "next/navigation";
import { api } from "../../../lib/api";

interface DocDetail {
  document_id: number;
  doc_number: string;
  revision: string;
  title: string;
  effective_status: string;
  author_username: string;
}

interface LineageItem {
  id: number;
  doc_number: string;
  revision: string;
  title: string;
  effective_status: string;
  effective_date: string | null;
  relation_type: string | null;
}

export default function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  const { data: doc, isLoading } = useQuery<DocDetail>({
    queryKey: ["document", id],
    queryFn: () =>
      api<DocDetail[]>(`/documents`).then((docs) => {
        const found = docs.find((d) => String(d.document_id) === id);
        if (!found) throw new Error("Document not found");
        return found;
      }),
  });

  const { data: lineage } = useQuery<{ chain: LineageItem[] }>({
    queryKey: ["lineage", id],
    queryFn: () => api(`/documents/${id}/lineage`),
    enabled: !!id,
  });

  if (isLoading) return <div style={{ padding: 40 }}>로딩 중...</div>;
  if (!doc) return <div style={{ padding: 40 }}>문서를 찾을 수 없습니다.</div>;

  const isEffective = doc.effective_status === "Effective" || doc.effective_status === "Superseded";

  return (
    <div style={{ maxWidth: 800, margin: "40px auto", padding: 24 }}>
      <button onClick={() => router.push("/documents")} style={{ marginBottom: 16, color: "#2563eb", background: "none", border: "none", cursor: "pointer", textDecoration: "underline" }}>
        ← 목록으로
      </button>
      <h1 style={{ fontSize: 22, fontWeight: "bold", marginBottom: 8 }}>{doc.title}</h1>
      <div style={{ color: "#6b7280", marginBottom: 24 }}>
        <span style={{ fontFamily: "monospace", marginRight: 16 }}>{doc.doc_number}</span>
        <span style={{ marginRight: 16 }}>개정: {doc.revision}</span>
        <span style={{ marginRight: 16 }}>상태: {doc.effective_status}</span>
        <span>작성자: {doc.author_username}</span>
      </div>

      {isEffective && (
        <div style={{ marginBottom: 24 }}>
          <button
            onClick={async () => {
              const token = localStorage.getItem("dms_token");
              const res = await fetch(`/api/documents/${id}/print`, {
                method: "POST",
                headers: {
                  "Content-Type": "application/json",
                  ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                body: JSON.stringify({ reason: "열람" }),
              });
              if (!res.ok) { alert("PDF 다운로드 실패"); return; }
              const blob = await res.blob();
              const url = URL.createObjectURL(blob);
              window.open(url, "_blank");
            }}
            style={{ display: "inline-block", padding: "8px 16px", background: "#2563eb", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>
            PDF 보기 / 인쇄
          </button>
        </div>
      )}

      {lineage && lineage.chain.length > 0 && (
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 12 }}>문서 이력 (Lineage)</h2>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {lineage.chain.map((item, i) => (
              <div key={item.id}
                style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 16px",
                  border: item.id === doc.document_id ? "2px solid #2563eb" : "1px solid #e5e7eb",
                  borderRadius: 6, background: item.id === doc.document_id ? "#eff6ff" : "white",
                  cursor: "pointer" }}
                onClick={() => router.push(`/documents/${item.id}`)}>
                {i > 0 && <span style={{ color: "#9ca3af" }}>↓</span>}
                <span style={{ fontFamily: "monospace", fontSize: 13 }}>{item.doc_number}</span>
                <span style={{ color: "#6b7280", fontSize: 13 }}>Rev {item.revision}</span>
                <span style={{ fontSize: 12, color: "#6b7280" }}>{item.relation_type ?? "신규"}</span>
                <span style={{ marginLeft: "auto", fontSize: 12, color: item.effective_status === "Effective" ? "#16a34a" : "#9ca3af" }}>
                  {item.effective_status}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
