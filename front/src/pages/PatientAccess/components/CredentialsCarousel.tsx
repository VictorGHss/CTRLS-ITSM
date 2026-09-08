import React from 'react';
import { User, MapPin, CreditCard } from 'lucide-react';
import type { AccessCredential } from '../types';
import type { ClinicTheme } from '../utils/clinicThemes';
import { CredentialCard } from './CredentialCard';

interface CredentialsCarouselProps {
  credentials: AccessCredential[];
  scrollRef: React.RefObject<HTMLDivElement | null>;
  activeCardIndex: number;
  onScroll: (e: React.UIEvent<HTMLDivElement>) => void;
  scrollToCard: (index: number) => void;
  onOpenFullscreen: (index: number) => void;
  onOpenCompanionModal: () => void;
  onReactivateAccess?: () => Promise<void>;
  onResetAccess?: () => void;
  onEditCpf?: (cred?: AccessCredential) => void;
  isReactivating?: boolean;
  reactivateMessage?: { type: 'success' | 'error'; text: string } | null;
  clinicTheme: ClinicTheme;
}

export const CredentialsCarousel: React.FC<CredentialsCarouselProps> = ({
  credentials,
  scrollRef,
  activeCardIndex,
  onScroll,
  scrollToCard,
  onOpenFullscreen,
  onOpenCompanionModal,
  onReactivateAccess,
  onResetAccess,
  onEditCpf,
  isReactivating,
  reactivateMessage,
  clinicTheme
}) => {
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-slate-800">Cartões de Acesso (Catraca)</h3>
        {credentials.length > 1 && (
          <span
            className="text-[10px] rounded-full px-2.5 py-0.5 font-bold"
            style={{
              backgroundColor: clinicTheme.secondaryColor,
              color: clinicTheme.primaryDarkColor
            }}
          >
            {activeCardIndex + 1} de {credentials.length} cartões
          </span>
        )}
      </div>

      {/* Slider de rolagem horizontal com snap CSS */}
      <div
        ref={scrollRef}
        onScroll={onScroll}
        className="flex gap-4 overflow-x-auto snap-x snap-mandatory scrollbar-none px-1 py-2"
        style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
      >
        {credentials.map((cred, idx) => (
          <CredentialCard
            key={cred.id || cred.credentialCode || idx}
            cred={cred}
            idx={idx}
            clinicTheme={clinicTheme}
            onOpenFullscreen={onOpenFullscreen}
            onReactivateAccess={onReactivateAccess}
            isReactivating={isReactivating}
            reactivateMessage={reactivateMessage}
            onEditCpf={onEditCpf}
          />
        ))}
      </div>

      {/* Bolinhas de Paginação do carrossel */}
      {credentials.length > 1 && (
        <div className="flex justify-center gap-1.5 mt-2">
          {credentials.map((_, idx) => (
            <button
              key={idx}
              onClick={() => scrollToCard(idx)}
              className={`h-2 rounded-full transition-all duration-300 cursor-pointer ${
                activeCardIndex === idx ? 'w-6' : 'w-2 bg-slate-200'
              }`}
              style={{
                backgroundColor: activeCardIndex === idx ? clinicTheme.primaryColor : undefined
              }}
              aria-label={`Ir para cartão ${idx + 1}`}
            />
          ))}
        </div>
      )}

      {/* Botões de Ação: Cadastrar Acompanhante e Reativar Acesso */}
      <div className="pt-4 flex flex-col gap-2.5">
        <button
          onClick={onOpenCompanionModal}
          className="w-full py-3 px-4 bg-white border rounded-2xl text-xs font-bold transition-all flex items-center justify-center gap-2 active:scale-[0.98] shadow-sm cursor-pointer"
          style={{
            borderColor: clinicTheme.primaryColor,
            color: clinicTheme.primaryDarkColor
          }}
        >
          <User className="w-4 h-4" style={{ color: clinicTheme.primaryColor }} />
          Cadastrar Acompanhante
        </button>

        {onEditCpf && (
          <button
            type="button"
            onClick={() => onEditCpf(credentials[activeCardIndex])}
            className="w-full py-3 px-4 bg-white border border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-700 rounded-2xl text-xs font-bold transition-all flex items-center justify-center gap-2 active:scale-[0.98] shadow-sm cursor-pointer"
          >
            <CreditCard className="w-4 h-4 text-slate-500" />
            <span>Corrigir CPF</span>
          </button>
        )}

        {onResetAccess && (
          <button
            type="button"
            onClick={onResetAccess}
            className="w-full py-2.5 px-4 text-slate-400 hover:text-slate-600 text-xs font-semibold transition-all flex items-center justify-center gap-1.5 cursor-pointer mt-0.5"
          >
            <span>Fazer novo cadastro ou trocar CPF</span>
          </button>
        )}
      </div>

      {/* Card de Localização / Como Chegar */}
      <div className="mt-4 bg-slate-50/50 backdrop-blur-sm border border-slate-200/50 shadow-md rounded-2xl p-5 flex flex-col space-y-3">
        <div className="flex items-center gap-2">
          <MapPin className="w-5 h-5" style={{ color: clinicTheme.primaryColor }} />
          <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider">{clinicTheme.name}</h4>
        </div>
        <p className="text-xs font-semibold text-slate-600 leading-relaxed">
          {clinicTheme.address}
        </p>
        <a
          href={clinicTheme.mapsUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="w-full py-3 active:scale-[0.98] text-white rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 shadow-sm cursor-pointer hover:opacity-95"
          style={{
            backgroundImage: `linear-gradient(to right, ${clinicTheme.primaryColor}, ${clinicTheme.primaryDarkColor})`
          }}
        >
          <MapPin className="w-4 h-4 text-white" />
          Abrir no Google Maps
        </a>
      </div>
    </div>
  );
};
