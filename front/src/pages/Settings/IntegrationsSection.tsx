import { MessageSquare, DollarSign, MessageCircle } from 'lucide-react';
import type { AdminConfig } from '@/types/models';

interface IntegrationsSectionProps {
  adminConfig: AdminConfig | null;
  discordWebhookStatus: string | null;
}

function WebhookBadge({ status }: { status: string }) {
  let text = 'Desconhecido';
  let cls = 'bg-amber-100 text-amber-700';
  switch (status) {
    case 'PRESENT':
      text = 'Presente';
      cls = 'bg-emerald-100 text-emerald-700';
      break;
    case 'INVALID':
      text = 'Erro/Inválido';
      cls = 'bg-red-100 text-red-700';
      break;
    case 'MISSING':
      text = 'Ausente';
      cls = 'bg-slate-100 text-slate-500';
      break;
    default:
      text = 'Desconhecido';
      cls = 'bg-amber-100 text-amber-700';
  }
  return <span className={`${cls} text-xs font-medium px-2 py-1 rounded`}>{text}</span>;
}

function PresenceBadge({ present }: { present?: boolean }) {
  const isPresent = Boolean(present);
  const text = isPresent ? 'Configurado' : 'Ausente';
  const cls = isPresent ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500';
  return <span className={`${cls} text-xs font-medium px-2 py-1 rounded`}>{text}</span>;
}

export default function IntegrationsSection({
  adminConfig,
  discordWebhookStatus,
}: IntegrationsSectionProps) {
  return (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
      {/* Card: Discord */}
      <div className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 flex flex-col justify-between transition-all hover:shadow-md hover:border-[#feb56c]/60 shadow-sm animate-fade-in">
        <div>
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#feb56c]/10 flex items-center justify-center text-[#feb56c] shrink-0 shadow-sm">
                <MessageSquare size={20} />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800">Discord</h3>
                <p className="text-xs text-slate-400">Notificações e alertas</p>
              </div>
            </div>
            <PresenceBadge present={adminConfig?.discordWebhookPresent || adminConfig?.discordWebhookUrlPresent} />
          </div>
          <div className="space-y-2 mt-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Webhook URL</span>
              <PresenceBadge present={adminConfig?.discordWebhookUrlPresent ?? adminConfig?.discordWebhookPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Token do Bot</span>
              <PresenceBadge present={adminConfig?.discordBotTokenPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Status do Webhook</span>
              <WebhookBadge status={discordWebhookStatus ?? 'UNKNOWN'} />
            </div>
          </div>
          <p className="mt-3 text-[11px] text-slate-400 font-medium">Somente leitura. Valores configurados via ENV.</p>
        </div>
      </div>

      {/* Card: Conta Azul */}
      <div className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 flex flex-col justify-between transition-all hover:shadow-md hover:border-[#feb56c]/60 shadow-sm animate-fade-in">
        <div>
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#feb56c]/10 flex items-center justify-center text-[#feb56c] shrink-0 shadow-sm">
                <DollarSign size={20} />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800">Conta Azul</h3>
                <p className="text-xs text-slate-400">ERP e conciliação financeira</p>
              </div>
            </div>
            <PresenceBadge present={adminConfig?.contaAzulClientIdPresent && adminConfig?.contaAzulClientSecretPresent} />
          </div>
          <div className="space-y-2 mt-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Client ID</span>
              <PresenceBadge present={adminConfig?.contaAzulClientIdPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Client Secret</span>
              <PresenceBadge present={adminConfig?.contaAzulClientSecretPresent} />
            </div>
          </div>
          <p className="mt-3 text-[11px] text-slate-400 font-medium">Somente leitura. Valores configurados via ENV / OAuth.</p>
        </div>
      </div>

      {/* Card: Feegow */}
      <div className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 flex flex-col justify-between transition-all hover:shadow-md hover:border-[#feb56c]/60 shadow-sm animate-fade-in">
        <div>
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#feb56c]/10 flex items-center justify-center text-[#feb56c] shrink-0 shadow-sm">
                <MessageCircle size={20} />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800">Feegow Clinic</h3>
                <p className="text-xs text-slate-400">Prontuário eletrônico e agendamentos</p>
              </div>
            </div>
            <PresenceBadge present={adminConfig?.feegowApiKeyPresent} />
          </div>
          <div className="space-y-2 mt-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">API Key</span>
              <PresenceBadge present={adminConfig?.feegowApiKeyPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Unidade Padrão</span>
              <PresenceBadge present={adminConfig?.feegowUnitIdPresent} />
            </div>
          </div>
          <p className="mt-3 text-[11px] text-slate-400 font-medium">Somente leitura. Valores configurados via ENV.</p>
        </div>
      </div>

      {/* Card: Blip */}
      <div className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 flex flex-col justify-between transition-all hover:shadow-md hover:border-[#feb56c]/60 shadow-sm animate-fade-in">
        <div>
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#feb56c]/10 flex items-center justify-center text-[#feb56c] shrink-0 shadow-sm">
                <MessageCircle size={20} />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800">Blip</h3>
                <p className="text-xs text-slate-400">WhatsApp e atendimento automatizado</p>
              </div>
            </div>
            <PresenceBadge present={adminConfig?.blipApiKeyPresent && adminConfig?.blipBotIdPresent} />
          </div>
          <div className="space-y-2 mt-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">API Key</span>
              <PresenceBadge present={adminConfig?.blipApiKeyPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">ID do Bot</span>
              <PresenceBadge present={adminConfig?.blipBotIdPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Webhook Token</span>
              <PresenceBadge present={adminConfig?.blipWebhookTokenPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Webhook Secret</span>
              <PresenceBadge present={adminConfig?.blipWebhookSecretPresent} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Backup (ENV)</span>
              <span className="text-xs font-semibold text-slate-500">Gerenciado no servidor</span>
            </div>
          </div>
          <p className="mt-3 text-[11px] text-slate-400 font-medium">Somente leitura. Valores configurados via ENV.</p>
        </div>
      </div>
    </div>
  );
}
