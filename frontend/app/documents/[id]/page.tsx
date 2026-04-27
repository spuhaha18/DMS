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

interface ApprovalStep {
  step_order: number;
  role: string;
  assigned_username: string;
  status: string;
  comment: string | null;
  decided_at: string | null;
}

const statusColor: Record<string, string> = {
  Approved: "#16a34a",
  Rejected: "#dc2626",
  Pending: "#d97706",
};

const roleLabel: Record<string, string> = {
  Author: "작성자",
  Reviewer: "검토자",
  Approver: "승인자",
};

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

  const { data: approvals } = useQuery<ApprovalStep[]>({
    queryKey: ["approvals", id],
    queryFn: () => api(`/documents/${id}/approvals`),
    enabled: !!id,
  });

  if (isLoading) return <div style={{ padding: 40 }}>로딩 중...</div>;
  if (!doc) return (
    <div style={{ padding: 40 }}>
      <p style={{ marginBottom: 16, color: "#6b7280" }}>문서를 찾을 수 없습니다.</p>
      <button onClick={() => router.push("/documents")} style={{ color: "#2563eb", background: "none", border: "none", cursor: "pointer", textDecoration: "underline" }}>
        ← 문서 목록으로
      </button>
    </div>
  );

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

      {approvals && approvals.length > 0 && (
        <div style={{ marginBottom: 32 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 12 }}>결재 이력</h2>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {approvals.map((a) => (
              <div key={a.step_order} style={{
                display: "flex", alignItems: "flex-start", gap: 12,
                padding: "12px 16px", border: "1px solid #e5e7eb",
                borderRadius: 6, background: "white",
              }}>
                <div style={{ minWidth: 72, fontSize: 12, color: "#6b7280", paddingTop: 2 }}>
                  {roleLabel[a.role] ?? a.role}
                </div>
                <div style={{ flex: 1 }}>
                  <span style={{ fontWeight: 500, marginRight: 8 }}>{a.assigned_username}</span>
                  <span style={{ fontSize: 12, color: statusColor[a.status] ?? "#6b7280", fontWeight: 600 }}>
                    {a.status === "Approved" ? "승인" : a.status === "Rejected" ? "반려" : a.status === "Pending" ? "대기" : a.status}
                  </span>
                  {a.decided_at && (
                    <span style={{ fontSize: 12, color: "#9ca3af", marginLeft: 8 }}>
                      {new Date(a.decided_at).toLocaleString("ko-KR")}
                    </span>
                  )}
                  {a.comment && (
                    <p style={{ margin: "4px 0 0", fontSize: 13, color: "#374151" }}>{a.comment}</p>
                  )}
                </div>
              </div>
            ))}
          </div>
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
