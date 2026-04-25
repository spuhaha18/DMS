"use client";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "../../lib/api";
import { clearToken } from "../../lib/auth";

interface InboxItem {
  approval_id: number;
  document_id: number;
  doc_number: string;
  title: string;
  role: string;
  submitted_by: string;
}

export default function InboxPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const [deciding, setDeciding] = useState<number | null>(null);
  const [comment, setComment] = useState("");

  const { data, isLoading, error } = useQuery<InboxItem[]>({
    queryKey: ["inbox"],
    queryFn: () => api<InboxItem[]>("/workflow/inbox"),
  });

  const decideMut = useMutation({
    mutationFn: ({ approvalId, decision }: { approvalId: number; decision: string }) =>
      api(`/workflow/inbox/${approvalId}/decide`, {
        method: "POST",
        body: JSON.stringify({ decision, comment }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inbox"] });
      setDeciding(null);
      setComment("");
    },
  });

  function handleLogout() {
    clearToken();
    router.push("/login");
  }

  return (
    <div style={{ maxWidth: 800, margin: "40px auto", padding: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: "bold" }}>결재함</h1>
        <div>
          <button onClick={() => router.push("/submit")} style={{ marginRight: 8, padding: "8px 16px", background: "#2563eb", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>문서 상신</button>
          <button onClick={() => router.push("/documents")} style={{ marginRight: 8, padding: "8px 16px", background: "#6b7280", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>문서 목록</button>
          <button onClick={handleLogout} style={{ padding: "8px 16px", background: "#dc2626", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>로그아웃</button>
        </div>
      </div>

      {isLoading && <p>로딩 중...</p>}
      {error && <p style={{ color: "red" }}>오류: {(error as Error).message}</p>}
      {data && data.length === 0 && <p style={{ color: "#6b7280" }}>대기 중인 결재가 없습니다.</p>}
      {data && data.map((item) => (
        <div key={item.approval_id} style={{ border: "1px solid #e5e7eb", borderRadius: 8, padding: 16, marginBottom: 12 }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <div>
              <span style={{ fontWeight: 600 }}>{item.doc_number}</span>
              <span style={{ marginLeft: 8, color: "#6b7280" }}>{item.title}</span>
            </div>
            <span style={{ background: "#dbeafe", color: "#1d4ed8", padding: "2px 8px", borderRadius: 12, fontSize: 12 }}>{item.role}</span>
          </div>
          <p style={{ color: "#6b7280", fontSize: 14, marginTop: 4 }}>상신자: {item.submitted_by}</p>
          <div style={{ marginTop: 8, display: "flex", gap: 8, alignItems: "center" }}>
            <button onClick={() => router.push(`/documents/${item.document_id}`)} style={{ padding: "4px 12px", border: "1px solid #e5e7eb", borderRadius: 4, cursor: "pointer" }}>상세</button>
            {deciding === item.approval_id ? (
              <>
                <input value={comment} onChange={(e) => setComment(e.target.value)} placeholder="코멘트 (선택)" style={{ padding: "4px 8px", border: "1px solid #ccc", borderRadius: 4, flex: 1 }} />
                <button onClick={() => decideMut.mutate({ approvalId: item.approval_id, decision: "Approve" })} style={{ padding: "4px 12px", background: "#16a34a", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>승인</button>
                <button onClick={() => decideMut.mutate({ approvalId: item.approval_id, decision: "Reject" })} style={{ padding: "4px 12px", background: "#dc2626", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>반려</button>
                <button onClick={() => setDeciding(null)} style={{ padding: "4px 12px", border: "1px solid #e5e7eb", borderRadius: 4, cursor: "pointer" }}>취소</button>
              </>
            ) : (
              <button onClick={() => setDeciding(item.approval_id)} style={{ padding: "4px 12px", background: "#f59e0b", color: "white", border: "none", borderRadius: 4, cursor: "pointer" }}>결재</button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
