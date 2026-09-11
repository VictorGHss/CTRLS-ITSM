// Contexto de autenticação global da aplicação
import {
  createContext,
  useContext,
  useState,
  useCallback,
  useMemo,
  type ReactNode,
} from 'react';
import api from '../services/api';
import { resetInitialPassword } from '../services/userService';
import type { AuthResponseDTO, User } from '../types/models';

interface SignInCredentials {
  email: string;
  password: string;
}

interface SignInResult {
  status: 'AUTHENTICATED' | 'PASSWORD_RESET_REQUIRED';
  tempToken?: string;
  userId?: string;
}

interface AuthContextData {
  user: User | null;
  token: string | null;
  isTwoFactorVerified: boolean;
  invalidateTwoFactorVerification: () => void;
  signIn: (credentials: SignInCredentials) => Promise<SignInResult>;
  completeInitialPasswordReset: (payload: {
    tempToken: string;
    userId: string;
    newPassword: string;
  }) => Promise<void>;
  updateAuthToken: (nextToken: string, nextUser?: User | null) => void;
  signOut: () => void;
}

interface AuthProviderProps {
  children: ReactNode;
}

const AuthContext = createContext<AuthContextData>({} as AuthContextData);

const TOKEN_KEY = '@Itsm:token';
const USER_KEY = '@Itsm:user';

export function AuthProvider({ children }: AuthProviderProps) {
  const [token, setToken] = useState<string | null>(
    () => localStorage.getItem(TOKEN_KEY),
  );
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem(USER_KEY);
    return stored ? (JSON.parse(stored) as User) : null;
  });
  const [forceTwoFactorUnverified, setForceTwoFactorUnverified] = useState(false);

  const isTwoFactorVerified = useMemo(
    () => getTwoFactorClaimFromToken(token) && !forceTwoFactorUnverified,
    [token, forceTwoFactorUnverified],
  );

  const invalidateTwoFactorVerification = useCallback(() => {
    setForceTwoFactorUnverified(true);
  }, []);

  // Realiza login e persiste credenciais no localStorage
  const signIn = useCallback(async ({ email, password }: SignInCredentials) => {
    const { data } = await api.post<AuthResponseDTO>(
      '/auth/login',
      { email, password },
    );

    if (data.status === 'PASSWORD_RESET_REQUIRED' && data.tempToken && data.userId) {
      return {
        status: 'PASSWORD_RESET_REQUIRED' as const,
        tempToken: data.tempToken,
        userId: data.userId,
      };
    }

    if (!data.token || !data.user) {
      throw new Error('Resposta de autenticação inválida.');
    }

    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    setToken(data.token);
    setUser(data.user);
    setForceTwoFactorUnverified(false);

    return { status: 'AUTHENTICATED' as const };
  }, []);

  const completeInitialPasswordReset = useCallback(async (payload: {
    tempToken: string;
    userId: string;
    newPassword: string;
  }) => {
    const response = await resetInitialPassword(payload);

    if (!response.token || !response.user) {
      throw new Error('Falha ao concluir redefinição de senha.');
    }

    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    setToken(response.token);
    setUser(response.user);
    setForceTwoFactorUnverified(false);
  }, []);

  const updateAuthToken = useCallback((nextToken: string, nextUser?: User | null) => {
    localStorage.setItem(TOKEN_KEY, nextToken);
    setToken(nextToken);
    setForceTwoFactorUnverified(false);

    if (nextUser) {
      localStorage.setItem(USER_KEY, JSON.stringify(nextUser));
      setUser(nextUser);
    }
  }, []);

  // Remove as credenciais e desconecta o usuário
  const signOut = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setToken(null);
    setUser(null);
    setForceTwoFactorUnverified(false);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isTwoFactorVerified,
        invalidateTwoFactorVerification,
        signIn,
        completeInitialPasswordReset,
        updateAuthToken,
        signOut,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// Hook para consumir o contexto de autenticação
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}

function getTwoFactorClaimFromToken(token: string | null): boolean {
  if (!token) {
    return false;
  }

  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return false;
    }

    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64)) as { two_factor_verified?: boolean };
    return payload.two_factor_verified === true;
  } catch {
    return false;
  }
}

