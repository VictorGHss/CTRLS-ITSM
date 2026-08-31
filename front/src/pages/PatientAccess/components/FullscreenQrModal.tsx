import React from 'react';
import { QRCodeSVG } from 'qrcode.react';

interface FullscreenQrModalProps {
  modalRef: React.RefObject<HTMLDivElement | null>;
  title: string;
  qrCodeValue: string;
  onClose: () => void;
}

export const FullscreenQrModal: React.FC<FullscreenQrModalProps> = ({
  modalRef,
  title,
  qrCodeValue,
  onClose,
}) => {
  return (
    <div 
      ref={modalRef}
      className="fixed inset-0 z-50 flex flex-col items-center justify-between p-6 sm:p-8"
      style={{ backgroundColor: '#ffffff' }}
    >
      <div className="text-center mt-6">
        <span className="text-[11px] font-extrabold tracking-wider text-brand-primary uppercase block">Catraca de Acesso Físico</span>
        <h4 className="text-lg sm:text-xl font-black text-slate-800 mt-1">{title}</h4>
        <div className="inline-flex items-center gap-1.5 bg-emerald-50 border border-emerald-200 text-emerald-700 px-3 py-1 rounded-full text-[11px] font-bold mt-2 shadow-xs">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          Acesso Liberado para a Catraca
        </div>
        <p className="text-xs text-slate-500 mt-2 font-medium">💡 Mantenha o celular a cerca de <b>10 a 15 cm</b> da câmera da catraca</p>
      </div>

      <div className="flex flex-col items-center justify-center flex-1 my-4 w-full max-w-sm">
        <div className="p-5 bg-white border-2 border-brand-primary/30 rounded-3xl shadow-2xl flex items-center justify-center">
          <QRCodeSVG 
            value={qrCodeValue} 
            size={280} 
            fgColor="#000000" 
            bgColor="#ffffff"
            level="M"
            marginSize={2}
          />
        </div>
        <span className="text-[11px] font-mono text-slate-400 mt-3 font-semibold">Trava de brilho da tela ativada</span>
      </div>

      <button 
        onClick={onClose}
        className="w-full max-w-sm py-4 bg-gradient-to-r from-brand-primary to-brand-primary-dark active:scale-[0.98] text-white rounded-2xl font-bold tracking-wide transition-all duration-300 shadow-lg shadow-brand-primary/20 cursor-pointer text-sm"
      >
        Fechar Tela Cheia
      </button>
    </div>
  );
};
