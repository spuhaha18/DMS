"use client";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "../../lib/api";

interface DocItem {
  document_id: number;
  doc_number: string;
  revision: string;
  title: string;
  effective_status: string;
  author_username: string;
}

const STATUS_COLORS: Record<string, string> = {
  Draft: "#9ca3af",
  UnderReview: "#f59e0b",
  PendingApproval: "#f97316",
  Effective: "#16a34a",
  Superseded: "#6b7280",
  Obsolete: "#dc2626",
};

export default function DocumentsPage() {
  const router = useRouter();
  const [searchQ, setSearchQ] = useState("");
  const [submittedQ, setSubmittedQ] = useState("");

  const { data, isLoading } = useQuery<DocItem[]>({
    queryKey: ["documents", submittedQ],
    queryFn: () =>
      submittedQ
        ? api<{ results: DocItem[] }>(`/documents/search?q=${encodeURIComponent(submittedQ)}`).then((d) => d.results)
        : api<DocItem[]>("/documents"),
  });

  return (
    <div style={{ maxWidth: 900, margin: "40px auto", padding: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: "bold" }}>문서 목록</h1>
        <button onClick={() => router.push("/inbox")} style={{ padding: "8px 16px", background: "#6b7280", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>결재함</button>
      </div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
        <input
          value={searchQ}
          onChange={(e) => setSearchQ(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && setSubmittedQ(searchQ)}
          placeholder="문서번호 또는 제목 검색..."
          style={{ flex: 1, padding: "8px 12px", border: "1px solid #ccc", borderRadius: 4 }}
        />
        <button onClick={() => setSubmittedQ(searchQ)} style={{ padding: "8px 16px", background: "#2563eb", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>검색</button>
        {submittedQ && <button onClick={() => { setSearchQ(""); setSubmittedQ(""); }} style={{ padding: "8px 16px", border: "1px solid #ccc", borderRadius: 4, cursor: "pointer" }}>초기화</button>}
      </div>

      {isLoading && <p>로딩 중...</p>}
      {data && data.length === 0 && <p style={{ color: "#6b7280" }}>문서가 없습니다.</p>}
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr style={{ borderBottom: "2px solid #e5e7eb" }}>
            {["문서번호", "개정", "제목", "상태", "작성자"].map((h) => (
              <th key={h} style={{ padding: "8px 12px", textAlign: "left", fontSize: 14, color: "#374151" }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data?.map((doc) => (
            <tr key={doc.document_id} style={{ borderBottom: "1px solid #f3f4f6", cursor: "pointer" }}
              onClick={() => router.push(`/documents/${doc.document_id}`)}>
              <td style={{ padding: "10px 12px", color: "#2563eb", fontFamily: "monospace" }}>{doc.doc_number}</td>
              <td style={{ padding: "10px 12px" }}>{doc.revision}</td>
              <td style={{ padding: "10px 12px" }}>{doc.title}</td>
              <td style={{ padding: "10px 12px" }}>
                <span style={{ background: STATUS_COLORS[doc.effective_status] ?? "#9ca3af", color: "white", padding: "2px 8px", borderRadius: 12, fontSize: 12 }}>
                  {doc.effective_status}
                </span>
              </td>
              <td style={{ padding: "10px 12px", color: "#6b7280" }}>{doc.author_username}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
