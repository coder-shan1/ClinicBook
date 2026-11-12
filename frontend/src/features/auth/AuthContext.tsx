import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import {
  clearTokens,
  getRefreshToken,
  setTokens,
} from '../../api/client'
import type { UserResponse, AuthResponse, LogoutRequest } from '../../api/types'
import { logout as apiLogout, refreshTokens } from '../../api/endpoints'

export interface AuthState {
  user: UserResponse | null
  /** True while the initial refresh-on-mount is in progress. */
  isLoading: boolean
  isAuthenticated: boolean
}

export interface AuthContextValue extends AuthState {
  /** Call after a successful login/register to store tokens and update state. */
  handleAuthResponse: (res: AuthResponse) => void
  /** Revoke the current refresh token and clear state. */
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/** Milliseconds before the access token expires at which we proactively refresh.
 *  expiresIn is 900 s (15 min); we refresh at 60 s before expiry = 840 s = 840_000 ms. */
const REFRESH_BEFORE_MS = 60_000

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearRefreshTimer = () => {
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current)
      refreshTimerRef.current = null
    }
  }

  const scheduleRefresh = useCallback((expiresInSeconds: number) => {
    clearRefreshTimer()
    const delayMs = Math.max(0, expiresInSeconds * 1000 - REFRESH_BEFORE_MS)
    refreshTimerRef.current = setTimeout(async () => {
      const rt = getRefreshToken()
      if (!rt) return
      try {
        const res = await refreshTokens({ refreshToken: rt })
        setTokens(res.accessToken, res.refreshToken)
        setUser(res.user)
        scheduleRefresh(res.expiresIn)
      } catch {
        // Refresh failed (token expired/revoked). Clear auth state so the UI
        // can redirect to login via the protected route guard.
        clearTokens()
        setUser(null)
      }
    }, delayMs)
  }, [])

  const handleAuthResponse = useCallback(
    (res: AuthResponse) => {
      setTokens(res.accessToken, res.refreshToken)
      setUser(res.user)
      scheduleRefresh(res.expiresIn)
    },
    [scheduleRefresh],
  )

  const signOut = useCallback(async () => {
    clearRefreshTimer()
    const rt = getRefreshToken()
    clearTokens()
    setUser(null)
    if (rt) {
      const req: LogoutRequest = { refreshToken: rt }
      await apiLogout(req).catch(() => {
        // Best-effort: even if the network call fails the client state is cleared.
      })
    }
  }, [])

  // On mount: attempt a silent refresh using the stored refresh token (if any).
  // This re-hydrates the session after a page reload.
  useEffect(() => {
    const rt = getRefreshToken()
    if (!rt) {
      setIsLoading(false)
      return
    }
    refreshTokens({ refreshToken: rt })
      .then((res) => {
        handleAuthResponse(res)
      })
      .catch(() => {
        clearTokens()
      })
      .finally(() => {
        setIsLoading(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Cleanup timer on unmount.
  useEffect(() => {
    return () => clearRefreshTimer()
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: user !== null,
      handleAuthResponse,
      signOut,
    }),
    [user, isLoading, handleAuthResponse, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>')
  return ctx
}
