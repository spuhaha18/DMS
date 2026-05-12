import axios from 'axios';

export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

// Guard to prevent concurrent logouts during simultaneous 401 errors
let logoutInProgress: Promise<void> | null = null;

api.interceptors.request.use((config) => {
  const method = (config.method ?? 'get').toLowerCase();
  if (['post', 'put', 'patch', 'delete'].includes(method)) {
    const xsrf = readCookie('XSRF-TOKEN');
    if (xsrf) config.headers.set('X-XSRF-TOKEN', xsrf);
  }
  return config;
});

// Response interceptor — handle 401 (force logout) and ProblemDetail errors (toast)
// Lazy-import router/store/toast inside the callback to avoid circular imports
// (router imports auth store which imports this module).
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status;

    // Don't toast/auth-redirect on the auth probe itself — let auth store handle it.
    // Don't toast PDF stream errors — PdfViewer renders them inline.
    const url: string = error.config?.url ?? '';
    const isAuthProbe = url.includes('/auth/me') || url.includes('/auth/login');
    const isPdfStream = url.includes('/pdf/view') || url.includes('/pdf/download');

    if (status === 401 && !isAuthProbe) {
      if (!logoutInProgress) {
        logoutInProgress = (async () => {
          try {
            const { useAuthStore } = await import('../stores/auth');
            const { router } = await import('../router');
            const auth = useAuthStore();
            try { await auth.logout(); } catch { /* swallow — already 401 */ }
            if (router.currentRoute.value.name !== 'login') {
              await router.push({ name: 'login' });
            }
          } catch {
            // module load failed — best-effort
          }
        })().finally(() => { logoutInProgress = null });
      }
      await logoutInProgress;
    } else if (status && status >= 400 && !isPdfStream) {
      try {
        const { emitToast } = await import('../components/Toast/useToast');
        const problem = error.response?.data;
        const msg = problem?.message ?? error.message ?? '알 수 없는 오류가 발생했습니다';

        // NOT_READY special case — show only warning, not error toast
        if (problem?.code === 'NOT_READY') {
          const retrySeconds = problem?.retryAfterSeconds ?? 30;
          emitToast(`PDF 변환 중입니다. ${retrySeconds}초 후 다시 시도해주세요.`, 'warning');
        } else {
          emitToast(msg, 'error');
        }
      } catch {
        // toast module not available — silently drop
      }
    }
    return Promise.reject(error);
  },
);
