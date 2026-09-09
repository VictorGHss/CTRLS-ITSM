import React, { useState, useEffect, useRef } from 'react';
import { QRCodeCanvas } from 'qrcode.react';
import { Sun, ArrowRightLeft, ChevronLeft, ChevronRight, X, Share2, Download, Check } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';
import type { AccessCredential } from '../types';
import { shareQrCodeImage, downloadQrCodeImage } from '../utils/shareQrCode';

interface FullscreenQrModalProps {
  modalRef: React.RefObject<HTMLDivElement | null>;
  credentials: AccessCredential[];
  currentIndex: number;
  onSwitchCard: (index: number) => void;
  onClose: () => void;
  clinicTheme?: ClinicTheme;
  title?: string;
  qrCodeValue?: string;
}

export const FullscreenQrModal: React.FC<FullscreenQrModalProps> = ({
  modalRef,
  credentials,
  currentIndex,
  onSwitchCard,
  onClose,
  clinicTheme,
  title: legacyTitle,
  qrCodeValue: legacyQrCodeValue,
}) => {
  const primaryColor = clinicTheme?.primaryColor || '#00875F';
  const primaryDarkColor = clinicTheme?.primaryDarkColor || '#00583F';

  const [qrSize, setQrSize] = useState<number>(310);
  const [isSharing, setIsSharing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isSaved, setIsSaved] = useState(false);
  const touchStartXRef = useRef<number | null>(null);

  const total = credentials.length;
  const hasMultiple = total > 1;
  const currentCred = credentials[currentIndex] || credentials[0];
  const nextIndex = (currentIndex + 1) % total;
  const prevIndex = (currentIndex - 1 + total) % total;
  const nextCred = credentials[nextIndex];

  const qrValue = currentCred?.credentialCode || legacyQrCodeValue || '';
  const displayTitle = currentCred 
    ? `${currentCred.userType === 'PATIENT' ? 'Titular' : 'Acompanhante'}: ${currentCred.name}`
    : legacyTitle || 'Acesso Físico';

  // Calcula tamanho ideal do QR Code para ocupar o maximo da tela do celular sem quebrar layout
  useEffect(() => {
    const calculateSize = () => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const targetByWidth = Math.floor(w * 0.85);
      const targetByHeight = Math.floor(h * 0.44);
      const ideal = Math.min(targetByWidth, targetByHeight, 350);
      setQrSize(Math.max(ideal, 270));
    };
    calculateSize();
    window.addEventListener('resize', calculateSize);
    return () => window.removeEventListener('resize', calculateSize);
  }, []);

  // Mantém a tela acesa impedindo auto-dimming ou bloqueio durante aproximação da catraca
  useEffect(() => {
    let wakeLockSentinel: WakeLockSentinel | null = null;
    if ('wakeLock' in navigator && navigator.wakeLock) {
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

  // Suporte a swipe horizontal com o dedo
  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartXRef.current = e.touches[0].clientX;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartXRef.current === null || !hasMultiple) return;
    const touchEndX = e.changedTouches[0].clientX;
    const diff = touchStartXRef.current - touchEndX;

    if (Math.abs(diff) > 50) {
      if (diff > 0) {
        // Deslizou para esquerda -> próximo
        onSwitchCard(nextIndex);
      } else {
        // Deslizou para direita -> anterior
        onSwitchCard(prevIndex);
      }
    }
    touchStartXRef.current = null;
  };

  return (
    <div 
      ref={modalRef}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      className="fixed inset-0 z-50 flex flex-col items-center justify-between p-4 sm:p-6 select-none bg-white"
      style={{ 
        colorScheme: 'light',
        forcedColorAdjust: 'none',
        filter: 'none',
        WebkitFilter: 'none',
        isolation: 'isolate'
      }}
    >
      {/* Top Header */}
      <div className="text-center mt-1 sm:mt-4 flex flex-col items-center max-w-sm w-full relative">
        {/* Botão Fechar no canto superior */}
        <button
          type="button"
          onClick={onClose}
          className="absolute -top-1 right-0 p-2 text-slate-400 hover:text-slate-600 rounded-full hover:bg-slate-100 transition-colors"
          aria-label="Fechar tela cheia"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Indicador de cartão múltiplo */}
        {hasMultiple ? (
          <div className="flex items-center gap-1.5 mb-1">
            <span 
              className={`text-[10px] font-extrabold uppercase px-2.5 py-0.5 rounded-full tracking-wider border ${
                currentCred?.userType === 'PATIENT' 
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-200' 
                  : 'bg-indigo-50 text-indigo-700 border-indigo-200'
              }`}
            >
              {currentCred?.userType === 'PATIENT' ? 'Paciente Titular' : 'Acompanhante'}
            </span>
            <span className="text-[10px] font-bold text-slate-400">
              ({currentIndex + 1} de {total})
            </span>
          </div>
        ) : (
          <span 
            className="text-[11px] font-extrabold tracking-wider uppercase block mb-1"
            style={{ color: primaryColor }}
          >
            Catraca de Acesso Físico
          </span>
        )}

        <h4 className="text-lg sm:text-xl font-black text-slate-800 line-clamp-1">
          {displayTitle}
        </h4>
        
        <div className="inline-flex items-center gap-1.5 bg-emerald-50 border border-emerald-200 text-emerald-700 px-3 py-0.5 rounded-full text-[10.5px] font-bold mt-1 shadow-xs">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          Acesso Liberado para a Catraca
        </div>

        {/* Alerta Proeminente de Brilho e Distância Correta */}
        <div className="bg-amber-50 border border-amber-300 text-amber-950 px-3 py-1.5 rounded-2xl text-[11px] font-bold mt-2 shadow-xs w-full text-center flex flex-col items-center gap-0.5">
          <div className="flex items-center gap-1.5 text-amber-800">
            <Sun className="w-3.5 h-3.5 text-amber-600 shrink-0" />
            <span>Aumente o brilho do celular ao máximo</span>
          </div>
          <span className="text-[10px] text-amber-700 font-semibold">
            📏 Mantenha a 15 cm da catraca (não encoste no vidro!)
          </span>
        </div>

        {/* Abas Rápidas no Modo Tela Cheia: Alternar entre Titular e Acompanhante */}
        {hasMultiple && (
          <div className="flex gap-1.5 p-1 bg-slate-100 rounded-xl border border-slate-200 mt-2.5 w-full shadow-xs">
            {credentials.map((c, idx) => {
              const isSelected = currentIndex === idx;
              const isPatient = c.userType === 'PATIENT';
              const shortName = c.name
                ? c.name.trim().split(' ')[0]
                : isPatient ? 'Titular' : `Acomp. ${idx}`;

              return (
                <button
                  key={c.id || c.credentialCode || idx}
                  type="button"
                  onClick={() => onSwitchCard(idx)}
                  className={`flex-1 py-1.5 px-2 rounded-lg text-[11px] font-bold transition-all flex items-center justify-center gap-1 cursor-pointer truncate ${
                    isSelected
                      ? 'bg-white text-slate-800 shadow-xs border border-slate-200/90'
                      : 'text-slate-500 hover:text-slate-700'
                  }`}
                >
                  <span className="truncate">
                    {isPatient ? `Titular: ${shortName}` : `Acomp.: ${shortName}`}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Central QR Code com Setas Laterais */}
      <div className="flex items-center justify-center flex-1 my-1 w-full max-w-sm relative">
        {hasMultiple && (
          <button
            type="button"
            onClick={() => onSwitchCard(prevIndex)}
            className="absolute -left-2 z-10 w-9 h-9 rounded-full bg-slate-100 hover:bg-slate-200 active:scale-95 text-slate-600 flex items-center justify-center shadow-md transition-all border border-slate-200"
            aria-label="QR Code Anterior"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>
        )}

        <div 
          className="p-3 sm:p-5 bg-white border-2 rounded-3xl shadow-2xl flex items-center justify-center transition-all duration-300"
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
            id="fullscreen-qr-canvas"
            value={qrValue} 
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

        {hasMultiple && (
          <button
            type="button"
            onClick={() => onSwitchCard(nextIndex)}
            className="absolute -right-2 z-10 w-9 h-9 rounded-full bg-slate-100 hover:bg-slate-200 active:scale-95 text-slate-600 flex items-center justify-center shadow-md transition-all border border-slate-200"
            aria-label="Próximo QR Code"
          >
            <ChevronRight className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* Footer / Ações */}
      <div className="w-full max-w-sm flex flex-col gap-2">
        {/* Botões Salvar no Celular e Compartilhar Imagem em Tela Cheia */}
        {qrValue && !qrValue.startsWith('CRED-') && qrValue !== 'BLOCKED_OUTSIDE_WINDOW' && qrValue !== 'CPF_MISSING' && (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={async () => {
                setIsSaved(false);
                setIsSaving(true);
                const ok = await downloadQrCodeImage(
                  'fullscreen-qr-canvas',
                  currentCred?.name || 'acesso',
                  currentCred?.userType || 'PATIENT',
                  currentCred?.doctorName || clinicTheme?.name,
                  currentCred?.locator
                );
                setIsSaving(false);
                if (ok) {
                  setIsSaved(true);
                  setTimeout(() => setIsSaved(false), 3500);
                }
              }}
              disabled={isSaving}
              className="flex-1 py-2.5 px-3 rounded-2xl font-bold flex items-center justify-center gap-2 text-xs sm:text-sm shadow-sm transition-all active:scale-[0.98] border cursor-pointer hover:bg-slate-50"
              style={isSaved ? {
                backgroundColor: `${clinicTheme?.secondaryColor || '#E6F4EA'}65`,
                borderColor: primaryColor,
                color: primaryDarkColor
              } : {
                backgroundColor: '#ffffff',
                borderColor: `${primaryColor}45`,
                color: primaryDarkColor
              }}
            >
              {isSaved ? (
                <>
                  <Check className="w-4 h-4 shrink-0" style={{ color: primaryDarkColor }} />
                  <span>Salvo no Celular!</span>
                </>
              ) : (
                <>
                  <Download className="w-4 h-4 shrink-0" style={{ color: primaryColor }} />
                  <span>{isSaving ? 'Salvando...' : 'Salvar no Celular'}</span>
                </>
              )}
            </button>

            <button
              type="button"
              onClick={async () => {
                setIsSharing(true);
                await shareQrCodeImage('fullscreen-qr-canvas', currentCred?.name || 'acesso', currentCred?.userType || 'PATIENT');
                setIsSharing(false);
              }}
              disabled={isSharing}
              className="py-2.5 px-3 rounded-2xl font-bold flex items-center justify-center gap-1.5 text-xs sm:text-sm shadow-sm transition-all active:scale-[0.98] border cursor-pointer shrink-0 hover:bg-slate-50"
              style={{
                backgroundColor: '#ffffff',
                borderColor: `${primaryColor}45`,
                color: primaryDarkColor
              }}
              title="Compartilhar Imagem do QR Code"
            >
              <Share2 className="w-4 h-4 shrink-0" style={{ color: primaryColor }} />
              <span>{isSharing ? '...' : 'Compartilhar'}</span>
            </button>
          </div>
        )}

        {/* Botão de Troca Rápida de QR Code */}
        {hasMultiple && nextCred && (
          <button
            type="button"
            onClick={() => onSwitchCard(nextIndex)}
            className="w-full py-3 px-4 rounded-2xl font-bold flex items-center justify-center gap-2 text-xs sm:text-sm shadow-md transition-all active:scale-[0.98] border cursor-pointer"
            style={{
              backgroundColor: '#f8fafc',
              borderColor: `${primaryColor}60`,
              color: primaryDarkColor
            }}
          >
            <ArrowRightLeft className="w-4 h-4 shrink-0" style={{ color: primaryColor }} />
            <span className="truncate">
              Trocar: <b>{nextCred.userType === 'PATIENT' ? 'Titular' : 'Acompanhante'} ({nextCred.name})</b>
            </span>
          </button>
        )}

        <button 
          type="button"
          onClick={onClose}
          className="w-full py-3.5 active:scale-[0.98] text-white rounded-2xl font-bold tracking-wide transition-all duration-300 shadow-lg cursor-pointer text-xs sm:text-sm"
          style={{
            backgroundImage: `linear-gradient(to right, ${primaryColor}, ${primaryDarkColor})`
          }}
        >
          Fechar Tela Cheia
        </button>
      </div>
    </div>
  );
};
