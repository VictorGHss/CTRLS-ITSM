import React, { useState } from 'react';
import { QRCodeCanvas } from 'qrcode.react';
import {
  Lock,
  User,
  ShieldCheck,
  Calendar,
  Maximize2,
  MapPin,
  RefreshCw,
  Sun,
  Share2,
  Download,
  Check,
  AlertTriangle
} from 'lucide-react';
import { formatCpf } from '../types';
import type { AccessCredential } from '../types';
import { resolveDoctorLocation, resolveDoctorSpecialty } from '../utils/clinicThemes';
import type { ClinicTheme } from '../utils/clinicThemes';
import { AddToCalendarMenu } from './AddToCalendarMenu';
import { shareQrCodeImage, downloadQrCodeImage } from '../utils/shareQrCode';

interface CredentialCardProps {
  cred: AccessCredential;
  idx: number;
  clinicTheme: ClinicTheme;
  onOpenFullscreen: (index: number) => void;
  onReactivateAccess?: () => Promise<void>;
  isReactivating?: boolean;
  reactivateMessage?: { type: 'success' | 'error'; text: string } | null;
  onEditCpf?: (cred?: AccessCredential) => void;
}

export const CredentialCard: React.FC<CredentialCardProps> = ({
  cred,
  idx,
  clinicTheme,
  onOpenFullscreen,
  onReactivateAccess,
  isReactivating,
  reactivateMessage,
  onEditCpf
}) => {
  const [sharing, setSharing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const isPendingCpf = cred.credentialCode.startsWith('CRED-') || cred.credentialCode === 'CPF_MISSING';
  const isBlockedWindow = cred.credentialCode === 'BLOCKED_OUTSIDE_WINDOW';
  const isReleased = !isPendingCpf && !isBlockedWindow;

  return (
    <div className="w-[88%] shrink-0 snap-center bg-slate-50/50 backdrop-blur-sm border border-slate-200/50 shadow-md rounded-2xl p-5 flex flex-col justify-between">
      {/* Metade Superior: QR Code e Metadados do Acesso */}
      <div className="flex flex-col items-center w-full">
        {/* Tag de Tipo de Usuário no Topo do Cartão */}
        <div className="flex items-center gap-2 mb-3">
          <span
            className={`text-[10px] font-extrabold uppercase px-3 py-1 rounded-full tracking-wider border ${
              cred.userType === 'PATIENT' ? '' : 'bg-indigo-50 text-indigo-700 border-indigo-100'
            }`}
            style={
              cred.userType === 'PATIENT'
                ? {
                    backgroundColor: clinicTheme.secondaryColor,
                    color: clinicTheme.primaryDarkColor,
                    borderColor: `${clinicTheme.primaryColor}30`
                  }
                : undefined
            }
          >
            {cred.userType === 'PATIENT' ? 'Paciente Titular' : 'Acompanhante'}
          </span>
          {isPendingCpf ? (
            <span className="inline-flex items-center gap-1 bg-amber-50 border border-amber-200/80 text-amber-700 text-[10px] font-bold px-2 py-0.5 rounded-full">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
              CPF Pendente
            </span>
          ) : (
            isReleased && (
              <span className="inline-flex items-center gap-1 bg-emerald-50 border border-emerald-200/80 text-emerald-700 text-[10px] font-bold px-2 py-0.5 rounded-full">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                Liberado
              </span>
            )
          )}
        </div>

        {/* Bloco do QR Code protegido contra Force Dark Mode */}
        <div
          className="p-3.5 rounded-2xl shadow-sm flex flex-col items-center justify-center relative min-h-[192px] w-[192px]"
          style={{
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            colorScheme: 'light',
            forcedColorAdjust: 'none',
            filter: 'none',
            WebkitFilter: 'none',
            isolation: 'isolate'
          }}
        >
          {isPendingCpf ? (
            <div className="flex flex-col items-center justify-center text-center p-3 space-y-2 select-none">
              <div className="w-12 h-12 bg-amber-50 rounded-full flex items-center justify-center text-amber-600 border border-amber-100">
                <AlertTriangle className="w-6 h-6 text-amber-500" />
              </div>
              <span className="text-[11px] font-bold text-slate-700 uppercase tracking-wider block">
                Acesso Pendente
              </span>
              <span className="text-[10px] text-slate-500 font-medium leading-relaxed block max-w-[160px]">
                O CPF deste paciente está incorreto ou ausente. Corrija para liberar o acesso.
              </span>
            </div>
          ) : isBlockedWindow ? (
            <div className="flex flex-col items-center justify-center text-center p-2 space-y-2 select-none">
              <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center text-slate-400">
                <Lock className="w-5 h-5" />
              </div>
              <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                Acesso Bloqueado
              </span>
              <span className="text-[9.5px] text-slate-400 font-bold leading-snug block">
                Liberado a partir das {cred.opensAt} no dia da consulta
              </span>
            </div>
          ) : (
            <>
              <QRCodeCanvas
                id={`qr-canvas-${idx}`}
                value={cred.credentialCode}
                size={168}
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
              <div className="absolute top-2 right-2 flex items-center justify-center pointer-events-none">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
                <span className="absolute w-2 h-2 rounded-full bg-emerald-500"></span>
              </div>
            </>
          )}
        </div>

        {/* Dica de Brilho e Distância da Catraca */}
        {isReleased && (
          <div className="flex flex-col items-center gap-1 mt-2.5 text-center">
            <div className="inline-flex items-center gap-1.5 bg-amber-50 border border-amber-200/90 text-amber-800 px-2.5 py-1 rounded-full text-[10.5px] font-bold shadow-xs">
              <Sun className="w-3.5 h-3.5 text-amber-600 shrink-0" />
              <span>Aumente o brilho do celular</span>
            </div>
            <p className="text-[10px] text-slate-500 font-medium">
              📏 Mantenha a 15 cm da catraca (não encoste)
            </p>
          </div>
        )}

        {/* Localizador Catraca Discreto */}
        {!isPendingCpf && (
          <span className="text-[10.5px] font-bold text-slate-400 font-mono mt-1 uppercase tracking-wider">
            Código: {cred.locator}
          </span>
        )}

        {/* Botões de Ação do QR Code */}
        {isReleased && (
          <div className="w-full mt-3 flex flex-col gap-2">
            <button
              type="button"
              onClick={() => onOpenFullscreen(idx)}
              className="w-full py-2.5 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 shadow-sm hover:opacity-95 active:scale-[0.98] text-white cursor-pointer"
              style={{
                backgroundImage: `linear-gradient(to right, ${clinicTheme.primaryColor}, ${clinicTheme.primaryDarkColor})`
              }}
            >
              <Maximize2 className="w-3.5 h-3.5" />
              <span>Ampliar QR Code</span>
            </button>

            {onReactivateAccess && (
              <div className="flex flex-col gap-1">
                <button
                  type="button"
                  onClick={onReactivateAccess}
                  disabled={isReactivating}
                  className="w-full py-2 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-2 active:scale-[0.98] shadow-xs cursor-pointer disabled:opacity-60 border"
                  style={{
                    backgroundColor: `${clinicTheme.secondaryColor}40`,
                    borderColor: `${clinicTheme.primaryColor}50`,
                    color: clinicTheme.primaryDarkColor
                  }}
                  title="Se a catraca não liberar na entrada ou saída, clique para renovar o acesso"
                >
                  <RefreshCw
                    className={`w-3.5 h-3.5 shrink-0 ${isReactivating ? 'animate-spin' : ''}`}
                    style={{ color: clinicTheme.primaryDarkColor }}
                  />
                  <span>
                    {isReactivating ? 'Gerando Novo QR Code...' : 'Problemas na catraca? Atualizar QR Code'}
                  </span>
                </button>
                {reactivateMessage && (
                  <div
                    className="p-2 rounded-xl text-[11px] font-medium text-center flex items-center justify-center gap-1.5 transition-all border"
                    style={
                      reactivateMessage.type === 'success'
                        ? {
                            backgroundColor: `${clinicTheme.secondaryColor}60`,
                            borderColor: clinicTheme.primaryColor,
                            color: clinicTheme.primaryDarkColor
                          }
                        : {
                            backgroundColor: '#fef2f2',
                            borderColor: '#fecaca',
                            color: '#991b1b'
                          }
                    }
                  >
                    <span>{reactivateMessage.type === 'success' ? '✅' : '⚠️'}</span>
                    <span>{reactivateMessage.text}</span>
                  </div>
                )}
                <p className="text-[10px] text-slate-500 text-center font-medium leading-relaxed px-1">
                  💡 Se a catraca não liberar na <strong>entrada</strong> ou <strong>saída</strong>, clique acima para renovar.
                </p>
              </div>
            )}

            <div className="flex gap-2">
              <button
                type="button"
                onClick={async () => {
                  setSaved(false);
                  setSaving(true);
                  const ok = await downloadQrCodeImage(
                    `qr-canvas-${idx}`,
                    cred.name,
                    cred.userType,
                    cred.doctorName || clinicTheme.name,
                    cred.locator
                  );
                  setSaving(false);
                  if (ok) {
                    setSaved(true);
                    setTimeout(() => setSaved(false), 3500);
                  }
                }}
                disabled={saving}
                className="flex-1 py-2 px-2.5 rounded-xl text-[11px] font-bold transition-all flex items-center justify-center gap-1.5 active:scale-[0.98] shadow-xs cursor-pointer border hover:bg-slate-50"
                style={
                  saved
                    ? {
                        backgroundColor: `${clinicTheme.secondaryColor}65`,
                        borderColor: clinicTheme.primaryColor,
                        color: clinicTheme.primaryDarkColor
                      }
                    : {
                        backgroundColor: '#ffffff',
                        borderColor: `${clinicTheme.primaryColor}45`,
                        color: clinicTheme.primaryDarkColor
                      }
                }
              >
                {saved ? (
                  <>
                    <Check className="w-3.5 h-3.5 shrink-0" style={{ color: clinicTheme.primaryDarkColor }} />
                    <span>Salvo no Celular!</span>
                  </>
                ) : (
                  <>
                    <Download className="w-3.5 h-3.5 shrink-0" style={{ color: clinicTheme.primaryColor }} />
                    <span>{saving ? 'Salvando...' : 'Salvar no Celular'}</span>
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={async () => {
                  setSharing(true);
                  await shareQrCodeImage(`qr-canvas-${idx}`, cred.name, cred.userType);
                  setSharing(false);
                }}
                disabled={sharing}
                className="py-2 px-3 rounded-xl text-[11px] font-bold transition-all flex items-center justify-center gap-1.5 active:scale-[0.98] shadow-xs cursor-pointer shrink-0 border hover:bg-slate-50"
                style={{
                  backgroundColor: '#ffffff',
                  borderColor: `${clinicTheme.primaryColor}45`,
                  color: clinicTheme.primaryDarkColor
                }}
                title="Compartilhar QR Code"
              >
                <Share2 className="w-3.5 h-3.5 shrink-0" style={{ color: clinicTheme.primaryColor }} />
                <span>{sharing ? '...' : 'Compartilhar'}</span>
              </button>
            </div>
          </div>
        )}

        {/* Alerta de CPF Incorreto */}
        {isPendingCpf && onEditCpf && (
          <div className="w-full mt-3 p-3 rounded-2xl bg-amber-50/80 border border-amber-200 text-center space-y-2">
            <div className="flex items-center justify-center gap-1.5 text-amber-900 font-bold text-xs">
              <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
              <span>CPF com inconsistência</span>
            </div>
            <p className="text-[11px] text-slate-600 font-medium leading-relaxed">
              O CPF cadastrado está incorreto ou ausente. Corrija para liberar seu acesso na clínica.
            </p>
            <button
              type="button"
              onClick={() => onEditCpf(cred)}
              className="w-full py-2 px-3 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-bold transition-all shadow-xs cursor-pointer flex items-center justify-center gap-1.5"
            >
              <span>Corrigir CPF</span>
            </button>
          </div>
        )}
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
            {resolveDoctorSpecialty(cred.doctorName) && (
              <span className="text-[10px] text-slate-500 font-medium block mt-0.5">
                {resolveDoctorSpecialty(cred.doctorName)}
              </span>
            )}
          </div>
        </div>

        {cred.appointmentDateTime && (
          <div className="flex items-start gap-2.5 pb-2 border-b border-slate-200/40">
            <Calendar className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
            <div>
              <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Data e Horário</span>
              <span className="text-xs font-extrabold" style={{ color: clinicTheme.primaryDarkColor }}>
                {cred.appointmentDateTime}
              </span>
            </div>
          </div>
        )}

        {/* Localização da Sala / Andar */}
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
        <div>
          <AddToCalendarMenu
            doctorName={cred.doctorName}
            patientName={cred.name}
            dateTimeStr={cred.appointmentDateTime}
            clinicTheme={clinicTheme}
          />
        </div>
      )}
    </div>
  );
};
