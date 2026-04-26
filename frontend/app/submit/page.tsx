"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { api } from "../../lib/api";

export default function SubmitPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<Array<{ id: number; code: string; name: string }>>([]);
  const [docTypes, setDocTypes] = useState<Array<{ id: number; code: string; name: string; allowed_change_types: string[] }>>([]);
  const [projectCode, setProjectCode] = useState("");
  const [docTypeCode, setDocTypeCode] = useState("");
  const [changeType, setChangeType] = useState("New");
  const [parentDocNumber, setParentDocNumber] = useState("");
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    api<Array<{ id: number; code: string; name: string }>>("/master/projects").then(
      (d) => setProjects(Array.isArray(d) ? d : [])
    ).catch(() => {});
    api<Array<{ id: number; code: string; name: string; allowed_change_types: string[] }>>("/master/document-types").then(
      (d) => setDocTypes(Array.isArray(d) ? d : [])
    ).catch(() => {});
  }, []);

  const selectedDt = docTypes.find((dt) => dt.code === docTypeCode);
  const requiresParent = changeType !== "New";

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setSuccess("");
    if (!file) { setError("파일을 선택하세요"); return; }
    const fd = new FormData();
    fd.append("file", file);
    fd.append("project_code", projectCode);
    fd.append("doc_type_code", docTypeCode);
    fd.append("change_type", changeType);
    fd.append("title", title);
    if (requiresParent && parentDocNumber) fd.append("parent_doc_number", parentDocNumber);
    try {
      const token = localStorage.getItem("dms_token");
      const res = await fetch("/api/documents/submit", {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: fd,
      });
      if (!res.ok) { const t = await res.text(); throw new Error(t); }
      const data = await res.json();
      setSuccess(`상신 완료: ${data.doc_number} (개정: ${data.revision})`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "오류");
    }
  }

  return (
    <div style={{ maxWidth: 600, margin: "40px auto", padding: 24 }}>
      <h1 style={{ marginBottom: 24, fontSize: 22, fontWeight: "bold" }}>문서 상신</h1>
      <form onSubmit={handleSubmit} noValidate>
        <Field label="과제">
          <select value={projectCode} onChange={(e) => setProjectCode(e.target.value)} required style={inputStyle}>
            <option value="">선택</option>
            {projects.map((p) => <option key={p.code} value={p.code}>{p.name} ({p.code})</option>)}
          </select>
        </Field>
        <Field label="문서 유형">
          <select value={docTypeCode} onChange={(e) => { setDocTypeCode(e.target.value); setChangeType("New"); }} required style={inputStyle}>
            <option value="">선택</option>
            {docTypes.map((dt) => <option key={dt.code} value={dt.code}>{dt.name} ({dt.code})</option>)}
          </select>
        </Field>
        <Field label="변경 유형">
          <select value={changeType} onChange={(e) => setChangeType(e.target.value)} style={inputStyle}>
            {(selectedDt?.allowed_change_types ?? ["New"]).map((ct) => <option key={ct} value={ct}>{ct}</option>)}
          </select>
        </Field>
        {requiresParent && (
          <Field label="원본 문서번호">
            <input type="text" value={parentDocNumber} onChange={(e) => setParentDocNumber(e.target.value)}
              placeholder="예: PROJ-SOP-0001" style={inputStyle} required />
          </Field>
        )}
        <Field label="문서 제목">
          <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} required style={inputStyle} />
        </Field>
        <Field label=".docx 파일">
          <input type="file" accept=".docx" onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
        </Field>
        {error && <p style={{ color: "red", marginBottom: 12 }}>{error}</p>}
        {success && <p style={{ color: "green", marginBottom: 12 }}>{success}</p>}
        <button type="submit" style={btnStyle}>상신</button>
        <button type="button" onClick={() => router.push("/inbox")} style={{ ...btnStyle, background: "#6b7280", marginLeft: 8 }}>목록으로</button>
      </form>
    </div>
  );
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "8px", border: "1px solid #ccc", borderRadius: 4, marginBottom: 0 };
const btnStyle: React.CSSProperties = { padding: "10px 20px", background: "#2563eb", color: "white", border: "none", borderRadius: 4, cursor: "pointer" };
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>{label}</label>
      {children}
    </div>
  );
}
