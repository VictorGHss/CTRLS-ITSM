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

  const [qrSize, setQrSize] = React.useState<number>(310);

  // Calcula tamanho ideal do QR Code para ocupar o maximo da tela do celular sem quebrar layout
  React.useEffect(() => {
    const calculateSize = () => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const targetByWidth = Math.floor(w * 0.85);
      const targetByHeight = Math.floor(h * 0.48);
      const ideal = Math.min(targetByWidth, targetByHeight, 360);
      setQrSize(Math.max(ideal, 290));
    };
    calculateSize();
    window.addEventListener('resize', calculateSize);
    return () => window.removeEventListener('resize', calculateSize);
  }, []);

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
      className="fixed inset-0 z-50 flex flex-col items-center justify-between p-4 sm:p-8 select-none"
      style={{ 
        backgroundColor: '#ffffff',
        colorScheme: 'light',
        forcedColorAdjust: 'none',
        filter: 'none',
        WebkitFilter: 'none',
        isolation: 'isolate'
      }}
    >
      <div className="text-center mt-3 sm:mt-6 flex flex-col items-center max-w-sm w-full">
        <span 
          className="text-[11px] font-extrabold tracking-wider uppercase block"
          style={{ color: primaryColor }}
        >
          Catraca de Acesso Físico
        </span>
        <h4 className="text-lg sm:text-xl font-black text-slate-800 mt-1 line-clamp-1">{title}</h4>
        
        <div className="inline-flex items-center gap-1.5 bg-emerald-50 border border-emerald-200 text-emerald-700 px-3 py-1 rounded-full text-[11px] font-bold mt-1.5 shadow-xs">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          Acesso Liberado para a Catraca
        </div>

        {/* Alerta Proeminente de Brilho e Distância Correta */}
        <div className="bg-amber-50 border border-amber-300 text-amber-950 px-3.5 py-2 rounded-2xl text-xs font-bold mt-2.5 shadow-xs w-full text-center flex flex-col items-center gap-0.5">
          <div className="flex items-center gap-1.5 text-amber-800">
            <Sun className="w-4 h-4 text-amber-600 shrink-0" />
            <span>Aumente o brilho do celular ao máximo</span>
          </div>
          <span className="text-[11px] text-amber-700 font-semibold">
            📏 Mantenha a 15 cm da catraca (não encoste no vidro!)
          </span>
        </div>
      </div>

      <div className="flex flex-col items-center justify-center flex-1 my-2 w-full">
        <div 
          className="p-4 sm:p-6 bg-white border-2 rounded-3xl shadow-2xl flex items-center justify-center"
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
            size={qrSize} 
            fgColor="#000000" 
            bgColor="#ffffff"
            level="M"
            marginSize={3}
            style={{
              colorScheme: 'light',
              forcedColorAdjust: 'none',
              filter: 'none',
              WebkitFilter: 'none'
            }}
          />
        </div>
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
