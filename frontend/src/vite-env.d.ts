/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the backend API (e.g. http://localhost:8080/api/v1). */
  readonly VITE_API_URL?: string
  /** 'disabled' turns off MSW mocking and uses the real backend. Defaults to enabled. */
  readonly VITE_API_MOCKING?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
