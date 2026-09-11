// Tela de login do sistema CTRLS ITSM
import { useState, type FormEvent } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Mail, Lock, LogIn, Loader2 } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useBranding } from '../../contexts/BrandingContext';
import LoginField from './LoginField';

export default function Login() {
  const { signIn } = useAuth();
  const { branding } = useBranding();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await signIn({ email, password });

      if (result.status === 'PASSWORD_RESET_REQUIRED' && result.tempToken && result.userId) {
        sessionStorage.setItem(
          '@Itsm:firstAccess',
          JSON.stringify({ tempToken: result.tempToken, userId: result.userId }),
        );
        navigate('/primeiro-acesso', {
          replace: true,
          state: {
            tempToken: result.tempToken,
            userId: result.userId,
          },
        });
        return;
      }

      // Redireciona para o caminho anterior se existir, caso contrário vai para dashboard
      const destination = (location.state as { from?: string } | null)?.from || '/dashboard';
      navigate(destination);
    } catch {
      setError('E-mail ou senha inválidos. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-white to-brand-secondary/20 flex items-center justify-center px-4">
      <title>Entrar — {branding.appName || 'CTRLS ITSM'}</title>
      <meta name="description" content="Acesse o portal de atendimento e suporte" />
      <div className="w-full max-w-sm">
        {/* Logo dinâmico da empresa */}
        <div className="flex justify-center mb-8">
          {branding.logoUrl ? (
            <img
              src={branding.logoUrl}
              alt={branding.appName || 'CTRLS ITSM'}
              className="h-16 object-contain"
            />
          ) : (
            <div className="h-14 px-5 rounded-2xl bg-brand-primary text-slate-900 font-extrabold flex items-center justify-center text-lg shadow-sm tracking-tight select-none">
              {branding.appName || 'CTRLS ITSM'}
            </div>
          )}
        </div>

        {/* Card de login */}
        <form
          onSubmit={handleSubmit}
          className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8 flex flex-col gap-5"
        >
          <LoginField
            id="email"
            type="email"
            label="E-mail"
            placeholder="seu@email.com"
            value={email}
            onChange={setEmail}
            icon={<Mail size={16} className="text-slate-400" />}
          />

          <LoginField
            id="password"
            type="password"
            label="Senha"
            placeholder="••••••••"
            value={password}
            onChange={setPassword}
            icon={<Lock size={16} className="text-slate-400" />}
          />

          {/* Mensagem de erro */}
          {error && (
            <p className="text-red-500 text-sm text-center -mt-1">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="mt-1 flex items-center justify-center gap-2 bg-brand-primary hover:bg-brand-primary-dark disabled:opacity-60 disabled:cursor-not-allowed text-white font-semibold py-2.5 rounded-xl transition-colors"
          >
            {loading ? (
              <Loader2 size={18} className="animate-spin" />
            ) : (
              <LogIn size={18} />
            )}
            {loading ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
      </div>
    </div>
  );
}
