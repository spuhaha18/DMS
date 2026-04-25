export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("dms_token");
}

export function setToken(token: string): void {
  localStorage.setItem("dms_token", token);
}

export function clearToken(): void {
  localStorage.removeItem("dms_token");
}

export function isLoggedIn(): boolean {
  return !!getToken();
}
