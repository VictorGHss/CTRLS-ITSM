import React from 'react';
import { QRCodeCanvas } from 'qrcode.react';
import { Sun } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';

interface FullscreenQrModalProps {
  modalRef: React.RefObject<HTMLDivElement | null>;
  title: string;
  qrCodeValue: string;
  onClose: () => void;
  clinicTheme?: ClinicTheme;
}

export const FullscreenQrModal: React.FC<FullscreenQrModalProps> = ({
  modalRef,
  title,
  qrCodeValue,
  onClose,
  clinicTheme,
}) => {
  const primaryColor = clinicTheme?.primaryColor || '#00875F';
  const primaryDarkColor = clinicTheme?.primaryDarkColor || '#00583F';

  // Mantém a tela acesa impedindo auto-dimming ou bloqueio durante aproximação da catraca
  React.useEffect(() => {
    let wakeLockSentinel: any = null;
    if ('wakeLock' in navigator) {
      navigator.wakeLock.request('screen')
        .then((sentinel) => {
          wakeLockSentinel = sentinel;
        })
        .catch((err) => {
          console.log('[WakeLock] Não disponível ou recusado pelo navegador:', err);
        });
    }
    return () => {
      if (wakeLockSentinel) {
        wakeLockSentinel.release().catch(() => {});
      }
    };
  }, []);

  return (
    <div 
      ref={modalRef}
      className="fixed inset-0 z-50 flex flex-col items-center justify-between p-6 sm:p-8 select-none"
      style={{ 
        backgroundColor: '#ffffff',
        colorScheme: 'light',
        forcedColorAdjust: 'none',
        filter: 'none',
        WebkitFilter: 'none',
        isolation: 'isolate'
      }}
    >
      <div className="text-center mt-6 flex flex-col items-center">
        <span 
          className="text-[11px] font-extrabold tracking-wider uppercase block"
          style={{ color: primaryColor }}
        >
          Catraca de Acesso Físico
        </span>
        <h4 className="text-lg sm:text-xl font-black text-slate-800 mt-1">{title}</h4>
        
        <div className="inline-flex items-center gap-1.5 bg-emerald-50 border border-emerald-200 text-emerald-700 px-3 py-1 rounded-full text-[11px] font-bold mt-2 shadow-xs">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          Acesso Liberado para a Catraca
        </div>

        {/* Alerta Proeminente de Brilho da Tela */}
        <div className="flex items-center gap-2 bg-amber-50 border border-amber-300 text-amber-900 px-4 py-2 rounded-xl text-xs font-bold mt-3 shadow-xs">
          <Sun className="w-4 h-4 text-amber-600 shrink-0 animate-spin-slow" />
          <span>☀️ Aumente o brilho do celular para facilitar a leitura</span>
        </div>

        <p className="text-xs text-slate-500 mt-2 font-medium">
          💡 Mantenha o celular a cerca de <b>10 a 15 cm</b> da câmera da catraca
        </p>
      </div>

      <div className="flex flex-col items-center justify-center flex-1 my-4 w-full max-w-sm">
        <div 
          className="p-5 bg-white border-2 rounded-3xl shadow-2xl flex items-center justify-center"
          style={{ 
            backgroundColor: '#ffffff',
            borderColor: `${primaryColor}40`,
            colorScheme: 'light',
            forcedColorAdjust: 'none',
            filter: 'none',
            WebkitFilter: 'none',
            isolation: 'isolate'
          }}
        >
          <QRCodeCanvas 
            value={qrCodeValue} 
            size={280} 
            fgColor="#000000" 
            bgColor="#ffffff"
            level="M"
            marginSize={2}
            style={{
              colorScheme: 'light',
              forcedColorAdjust: 'none',
              filter: 'none',
              WebkitFilter: 'none'
            }}
          />
        </div>
        <span className="text-[11px] font-mono text-slate-400 mt-3 font-semibold">Trava anti-bloqueio da tela ativada</span>
      </div>

      <button 
        onClick={onClose}
        className="w-full max-w-sm py-4 active:scale-[0.98] text-white rounded-2xl font-bold tracking-wide transition-all duration-300 shadow-lg cursor-pointer text-sm"
        style={{
          backgroundImage: `linear-gradient(to right, ${primaryColor}, ${primaryDarkColor})`
        }}
      >
        Fechar Tela Cheia
      </button>
    </div>
  );
};
