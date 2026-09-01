import React from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { 
  Lock, 
  User, 
  ShieldCheck, 
  Calendar, 
  Maximize2, 
  MapPin,
  RefreshCw
} from 'lucide-react';
import { formatCpf } from '../types';
import type { AccessCredential } from '../types';
import { resolveDoctorLocation } from '../utils/clinicThemes';
import type { ClinicTheme } from '../utils/clinicThemes';
import { AddToCalendarMenu } from './AddToCalendarMenu';

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
  isReactivating?: boolean;
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
  isReactivating,
  clinicTheme,
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
            Deslize para o lado ({activeCardIndex + 1}/{credentials.length})
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
          <div 
            key={idx}
            className="w-[88%] shrink-0 snap-center bg-slate-50/50 backdrop-blur-sm border border-slate-200/50 shadow-md rounded-2xl p-5 flex flex-col justify-between"
          >
            {/* Metade Superior: QR Code e Metadados do Acesso */}
            <div className="flex flex-col items-center w-full">
              {/* Tag de Tipo de Usuário no Topo do Cartão */}
              <div className="flex items-center gap-2 mb-3">
                <span 
                  className={`text-[10px] font-extrabold uppercase px-3 py-1 rounded-full tracking-wider border ${
                    cred.userType === 'PATIENT' 
                      ? '' 
                      : 'bg-indigo-50 text-indigo-700 border-indigo-100'
                  }`}
                  style={cred.userType === 'PATIENT' ? {
                    backgroundColor: clinicTheme.secondaryColor,
                    color: clinicTheme.primaryDarkColor,
                    borderColor: `${clinicTheme.primaryColor}30`
                  } : undefined}
                >
                  {cred.userType === 'PATIENT' ? 'Paciente Titular' : 'Acompanhante'}
                </span>
                {cred.credentialCode !== 'BLOCKED_OUTSIDE_WINDOW' && cred.credentialCode !== 'CPF_MISSING' && (
                  <span className="inline-flex items-center gap-1 bg-emerald-50 border border-emerald-200/80 text-emerald-700 text-[10px] font-bold px-2 py-0.5 rounded-full">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                    Liberado
                  </span>
                )}
              </div>

              {/* Bloco do QR Code */}
              <div className="p-4 bg-white border border-slate-200 rounded-2xl shadow-sm flex flex-col items-center justify-center relative min-h-[184px] w-[184px]">
                {cred.credentialCode === 'BLOCKED_OUTSIDE_WINDOW' ? (
                  <div className="flex flex-col items-center justify-center text-center p-2 space-y-2 select-none">
                    <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center text-slate-400">
                      <Lock className="w-5 h-5" />
                    </div>
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">Acesso Bloqueado</span>
                    <span className="text-[9.5px] text-slate-400 font-bold leading-snug block">Liberado a partir das {cred.opensAt} no dia da consulta</span>
                  </div>
                ) : (
                  <>
                    <QRCodeSVG 
                      value={cred.credentialCode} 
                      size={160} 
                      fgColor="#000000" 
                      bgColor="#ffffff"
                      level="M"
                      marginSize={2}
                    />
                    <div className="absolute top-2 right-2 flex items-center justify-center">
                      <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
                      <span className="absolute w-2 h-2 rounded-full bg-emerald-500"></span>
                    </div>
                  </>
                )}
              </div>

              {/* Dica de Distância do Leitor */}
              <p className="text-[10px] text-slate-400 font-medium mt-2 text-center">
                💡 Aproxime a 10–15 cm da câmera da catraca
              </p>

              {/* Localizador Catraca Discreto */}
              <span className="text-[10.5px] font-bold text-slate-400 font-mono mt-1 uppercase tracking-wider">
                Ref: {cred.locator}
              </span>
            </div>

            {/* Divisor Tracejado Estilo Wallet */}
            <div className="w-full border-t border-dashed border-slate-300 my-4"></div>

            {/* Metade Inferior: Dados da Consulta */}
            <div className="w-full space-y-3 text-left mb-3">
              <div className="flex items-start gap-2.5 pb-2 border-b border-slate-200/40">
                <User className="w-4 h-4 shrink-0 mt-0.5" style={{ color: clinicTheme.primaryColor }} />
                <div>
                  <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Nome</span>
                  <span className="text-xs font-bold text-slate-700">{cred.name}</span>
                </div>
              </div>
              
              {cred.cpf && (
                <div className="flex items-start gap-2.5 pb-2 border-b border-slate-200/40">
                  <ShieldCheck className="w-4 h-4 shrink-0 mt-0.5" style={{ color: clinicTheme.primaryColor }} />
                  <div>
                    <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">CPF</span>
                    <span className="text-xs font-semibold text-slate-700">{formatCpf(cred.cpf)}</span>
                  </div>
                </div>
              )}
              
              <div className="flex items-start gap-2.5 pb-2 border-b border-slate-200/40">
                <User className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
                <div>
                  <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Médico / Especialista</span>
                  <span className="text-xs font-bold text-slate-800">{cred.doctorName || clinicTheme.name}</span>
                </div>
              </div>
              
              {cred.appointmentDateTime && (
                <div className="flex items-start gap-2.5 pb-2 border-b border-slate-200/40">
                  <Calendar className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Data e Horário</span>
                    <span className="text-xs font-extrabold" style={{ color: clinicTheme.primaryDarkColor }}>{cred.appointmentDateTime}</span>
                  </div>
                </div>
              )}

              {/* Localização da Sala / Andar diretamente no cartão */}
              <div className="flex items-start gap-2.5">
                <MapPin className="w-4 h-4 shrink-0 mt-0.5" style={{ color: clinicTheme.primaryColor }} />
                <div>
                  <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Local / Sala</span>
                  <span className="text-xs font-bold text-slate-700">
                    {resolveDoctorLocation(cred.doctorName, clinicTheme.floorInfo)}
                  </span>
                </div>
              </div>
            </div>

            {/* Menu Adicionar à Agenda */}
            {cred.appointmentDateTime && (
              <div className="mb-3">
                <AddToCalendarMenu
                  doctorName={cred.doctorName}
                  patientName={cred.name}
                  dateTimeStr={cred.appointmentDateTime}
                  clinicTheme={clinicTheme}
                />
              </div>
            )}

            {/* Botão Ampliar QR Code para tela cheia */}
            <button 
              onClick={() => onOpenFullscreen(idx)}
              disabled={cred.credentialCode === 'BLOCKED_OUTSIDE_WINDOW'}
              className={`w-full py-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 shadow-sm mt-auto ${
                cred.credentialCode === 'BLOCKED_OUTSIDE_WINDOW'
                  ? 'bg-slate-100 text-slate-400 cursor-not-allowed shadow-none'
                  : 'hover:opacity-95 active:scale-[0.98] text-white cursor-pointer'
              }`}
              style={cred.credentialCode !== 'BLOCKED_OUTSIDE_WINDOW' ? {
                backgroundImage: `linear-gradient(to right, ${clinicTheme.primaryColor}, ${clinicTheme.primaryDarkColor})`
              } : undefined}
            >
              <Maximize2 className="w-3.5 h-3.5" />
              Ampliar QR Code
            </button>
          </div>
        ))}
      </div>

      {/* Bolinhas de Paginação do carrossel */}
      {credentials.length > 1 && (
        <div className="flex justify-center gap-1.5 mt-2">
          {credentials.map((_, idx) => (
            <button 
              key={idx}
              onClick={() => scrollToCard(idx)}
              className={`h-2 rounded-full transition-all duration-300 ${
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

        {onReactivateAccess && (
          <div className="flex flex-col gap-1.5">
            <button
              onClick={onReactivateAccess}
              disabled={isReactivating}
              className="w-full py-3 px-4 bg-white border border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-700 rounded-2xl text-xs font-bold transition-all flex items-center justify-center gap-2 active:scale-[0.98] shadow-sm cursor-pointer disabled:opacity-60"
            >
              <RefreshCw className={`w-4 h-4 text-slate-500 ${isReactivating ? 'animate-spin' : ''}`} />
              {isReactivating ? 'Gerando Novo QR Code...' : 'Reativar Acesso (Entrar Novamente)'}
            </button>
            <p className="text-[10px] text-slate-400 text-center font-medium leading-relaxed px-2">
              💡 Precisou sair do prédio e vai entrar de novo? Clique acima para gerar um novo QR Code válido nas catracas.
            </p>
          </div>
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
