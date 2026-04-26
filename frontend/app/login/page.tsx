"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { setToken } from "../../lib/auth";

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const router = useRouter();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      if (!res.ok) throw new Error("로그인 실패");
      const data = await res.json();
      setToken(data.access_token);
      router.push("/inbox");
    } catch (err) {
      setError(err instanceof Error ? err.message : "오류가 발생했습니다");
    }
  }

  return (
    <div style={{ maxWidth: 400, margin: "100px auto", padding: 24 }}>
      <h1 style={{ marginBottom: 24, fontSize: 24, fontWeight: "bold" }}>DMS 로그인</h1>
      <form onSubmit={handleSubmit} noValidate>
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: "block", marginBottom: 4 }}>사용자명</label>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            style={{ width: "100%", padding: "8px", border: "1px solid #ccc", borderRadius: 4 }}
          />
        </div>
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: "block", marginBottom: 4 }}>비밀번호</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            style={{ width: "100%", padding: "8px", border: "1px solid #ccc", borderRadius: 4 }}
          />
        </div>
        {error && <p style={{ color: "red", marginBottom: 16 }}>{error}</p>}
        <button
          type="submit"
          style={{ width: "100%", padding: "10px", background: "#2563eb", color: "white", border: "none", borderRadius: 4, cursor: "pointer", fontSize: 16 }}
        >
          로그인
        </button>
      </form>
    </div>
  );
}
