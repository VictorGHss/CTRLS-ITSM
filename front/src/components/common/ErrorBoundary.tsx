import { Component, type ReactNode, type ErrorInfo } from 'react';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * ErrorBoundary robusto para capturar falhas de renderização e chunks no React 19.
 * Evita telas brancas e oferece interface amigável para recuperação pelo usuário.
 */
export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
      errorInfo: null,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({ error, errorInfo });
    console.error('[ErrorBoundary] Falha capturada na renderização:', error, errorInfo);
  }

  handleReset = (): void => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  handleReload = (): void => {
    window.location.reload();
  };

  handleGoHome = (): void => {
    window.location.href = '/dashboard';
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex min-h-screen w-full items-center justify-center bg-slate-50 p-6">
          <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-8 shadow-xl text-center">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-amber-50 text-amber-600 border border-amber-200 shadow-sm mb-5">
              <AlertTriangle size={32} />
            </div>

            <h1 className="text-xl font-bold text-slate-800 mb-2">
              Ops! Algo inesperado aconteceu
            </h1>

            <p className="text-sm text-slate-600 mb-6 leading-relaxed">
              Ocorreu um erro temporário na interface. Você pode tentar recarregar a tela ou voltar para o início.
            </p>

            {this.state.error && (
              <div className="mb-6 overflow-hidden rounded-xl border border-slate-100 bg-slate-50 p-3 text-left">
                <p className="font-mono text-xs text-rose-600 font-semibold truncate">
                  {this.state.error.name}: {this.state.error.message}
                </p>
              </div>
            )}

            <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
              <button
                type="button"
                onClick={this.handleReload}
                className="flex w-full sm:w-auto items-center justify-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-brand-primary-dark transition"
              >
                <RefreshCw size={14} />
                Recarregar Página
              </button>

              <button
                type="button"
                onClick={this.handleGoHome}
                className="flex w-full sm:w-auto items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
              >
                <Home size={14} />
                Ir para o Início
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
