import { useEffect, useMemo, useState, useCallback } from 'react';
import { Loader2, Save, Info, Search, Users, UserCheck, UserX, ShieldCheck, RefreshCw } from 'lucide-react';
import { toast } from 'react-toastify';
import SearchableDropdown from '@/components/common/SearchableDropdown';

import { getApiErrorMessage } from '../../lib/apiError';
import {
  getFeegowProfessionals,
  getMappings,
  getBlipQueuesList,
  syncMappings,
  type FeegowProfessional,
  type DoctorMapping,
  type BlipQueue,
} from '../../services/doctorMappingService';
import {
  getAll as getAllConfigs,
  save as saveConfig,
  updateDoctorActive,
  type DoctorConfiguration,
} from '../../services/doctorConfigService';

interface MergedDoctorMapping extends DoctorMapping {
  blip_queue_id?: string;
  itsm_user_id?: string;
  discord_webhook_url?: string;
  external_wa_link?: string;
  profissional_nome?: string;
  is_external?: boolean;
  ignore_auto_schedule?: boolean;
  isActive: boolean;
  
  // Consolidated GerAcesso fields
  gerAcessoMatricula?: string;
  gerAcessoCpf?: string;

  // Custom scheduling rules
  displayTimeOffsetMinutes?: number;
  advanceNoticeDays?: number;

  // Google Review URL
  googleReviewUrl?: string;
}

function formatCPF(value: string) {
  const digits = (value || '').replace(/\D/g, '');
  if (digits.length <= 3) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`;
  if (digits.length <= 9) return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`;
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9, 11)}`;
}

export default function ProfessionalMappingPanel() {
  const [professionals, setProfessionals] = useState<FeegowProfessional[]>([]);
  const [mappings, setMappings] = useState<MergedDoctorMapping[]>([]);
  const [blipQueues, setBlipQueues] = useState<BlipQueue[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [syncingData, setSyncingData] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  // Search and Filter states
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');

  const professionalsById = useMemo(() => {
    const map = new Map<string, FeegowProfessional>();
    professionals.forEach((p) => {
      const id = String(p.id ?? '').trim();
      if (!id) return;
      if (!map.has(id)) {
        map.set(id, p);
      }
    });
    return map;
  }, [professionals]);

  const missingIdCount = useMemo(() => {
    return mappings.filter((m) => !String(m.profissionalId ?? '').trim()).length;
  }, [mappings]);

  const totalCount = mappings.length;
  const activeCount = useMemo(() => mappings.filter((m) => m.isActive).length, [mappings]);
  const inactiveCount = totalCount - activeCount;

  const filteredMappings = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return mappings.filter((row) => {
      // 1. Filter by search term (ID or Name)
      if (term) {
        const idMatch = String(row.profissionalId ?? '').toLowerCase().includes(term);
        const nameMatch = String(row.profissionalNome ?? '').toLowerCase().includes(term);
        if (!idMatch && !nameMatch) return false;
      }

      // 2. Filter by active status
      if (filterStatus === 'ACTIVE') return row.isActive;
      if (filterStatus === 'INACTIVE') return !row.isActive;
      return true;
    });
  }, [mappings, searchTerm, filterStatus]);

  const hasBlipQueues = blipQueues.length > 0;

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [pros, dbMappings, dbConfigs, queues] = await Promise.all([
        getFeegowProfessionals(),
        getMappings(),
        getAllConfigs(),
        getBlipQueuesList(),
      ]);

      setProfessionals(Array.isArray(pros) ? pros : []);
      const queueList = Array.isArray(queues) ? queues : [];
      setBlipQueues(queueList);

      const mappingById = new Map<string, MergedDoctorMapping>();
      if (Array.isArray(dbMappings)) {
        (dbMappings as MergedDoctorMapping[]).forEach((m) => mappingById.set(String(m.profissionalId), m));
      }

      const configById = new Map<string, DoctorConfiguration>();
      if (Array.isArray(dbConfigs)) {
        dbConfigs.forEach((c) => configById.set(String(c.feegowProfissionalId), c));
      }

      // Collect all unique IDs from Feegow, mappings and configs
      const allProIds = new Set<string>();
      (Array.isArray(pros) ? pros : []).forEach((p) => allProIds.add(String(p.id)));
      if (Array.isArray(dbMappings)) {
        dbMappings.forEach((m) => { if (m.profissionalId) allProIds.add(String(m.profissionalId)); });
      }
      if (Array.isArray(dbConfigs)) {
        dbConfigs.forEach((c) => { if (c.feegowProfissionalId) allProIds.add(String(c.feegowProfissionalId)); });
      }

      const proMap = new Map<string, FeegowProfessional>();
      (Array.isArray(pros) ? pros : []).forEach((p) => {
        const id = String(p.id ?? '').trim();
        if (id && !proMap.has(id)) proMap.set(id, p);
      });

      // Sort IDs numerically if possible, otherwise alphabetically
      const sortedIds = Array.from(allProIds).sort((a, b) => {
        const numA = Number(a);
        const numB = Number(b);
        if (!isNaN(numA) && !isNaN(numB)) return numA - numB;
        return a.localeCompare(b);
      });

      const merged: MergedDoctorMapping[] = sortedIds.map((proId) => {
        const p = proMap.get(proId);
        const m = mappingById.get(proId);
        const c = configById.get(proId);

        let initialQueueId = m?.blipQueueId || m?.blip_queue_id || c?.blipQueueId || '';
        
        // Determine active status:
        // By default, if DB record explicitly says inactive (or ignoreAutoSchedule=true or queue='inactive'), it's false.
        // If neither config exists yet, default to false for safety.
        let isActive = true;
        if (c !== undefined) {
          isActive = c.isActive !== false;
        } else if (m !== undefined) {
          isActive = m.isActive !== false && !m.ignoreAutoSchedule;
        } else {
          // Brand new doctor not yet registered in database
          isActive = false;
        }

        if (initialQueueId === 'inactive' || m?.ignoreAutoSchedule === true || c?.isActive === false) {
          isActive = false;
        }

        return {
          id: m?.id,
          profissionalId: proId,
          blipQueueId: initialQueueId,
          itsmUserId: m?.itsmUserId || m?.itsm_user_id || '',
          discordWebhookUrl: m?.discordWebhookUrl || m?.discord_webhook_url || '',
          profissionalNome: m?.profissionalNome || m?.profissional_nome || c?.doctorName || p?.name || `Sem nome (ID ${proId})`,
          ignoreAutoSchedule: !isActive,
          isActive: isActive,
          gerAcessoMatricula: c?.gerAcessoMatricula || '',
          gerAcessoCpf: c?.gerAcessoCpf || '',
          displayTimeOffsetMinutes: c?.displayTimeOffsetMinutes ?? 0,
          advanceNoticeDays: c?.advanceNoticeDays ?? 1,
          googleReviewUrl: c?.googleReviewUrl || '',
        } as MergedDoctorMapping;
      });

      setMappings(merged);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao carregar profissionais ou mapeamentos.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  async function handleSyncData() {
    try {
      setSyncingData(true);
      await loadData();
      toast.success('Dados sincronizados com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao sincronizar dados.'));
    } finally {
      setSyncingData(false);
    }
  }

  const hasChanges = useMemo(() => true, []);

  function updateField(profissionalId: string, field: keyof MergedDoctorMapping, value: string | boolean | number) {
    setMappings((current) =>
      current.map((m) => {
        if (String(m.profissionalId) === String(profissionalId)) {
          const updated = { ...m, [field]: value };
          if (field === 'blipQueueId' && value === 'inactive') {
            updated.isActive = false;
            updated.ignoreAutoSchedule = true;
          } else if (field === 'isActive') {
            updated.ignoreAutoSchedule = !value;
            if (value === true && updated.blipQueueId === 'inactive') {
              updated.blipQueueId = '';
            }
          }
          return updated;
        }
        return m;
      })
    );
  }

  // Instant single-click toggle handler
  async function handleToggleActive(proId: string, currentActive: boolean, doctorName: string) {
    const newActive = !currentActive;
    const numericId = Number(proId);

    // Optimistic local update
    updateField(proId, 'isActive', newActive);

    if (isNaN(numericId)) {
      toast.warn(`ID do médico '${proId}' não é numérico.`);
      return;
    }

    try {
      setTogglingId(proId);
      await updateDoctorActive(numericId, newActive);
      if (newActive) {
        toast.success(`Confirmação automática ATIVADA para ${doctorName}!`);
      } else {
        toast.info(`Confirmação automática DESATIVADA para ${doctorName}.`);
      }
    } catch (error) {
      // Revert on failure
      updateField(proId, 'isActive', currentActive);
      toast.error(getApiErrorMessage(error, `Falha ao alterar status de ${doctorName}.`));
    } finally {
      setTogglingId(null);
    }
  }

  async function handleSave() {
    try {
      setSaving(true);

      // 1. Save appointment doctor mappings
      const mappingPayload = mappings.map((m) => {
        return {
          profissionalId: String(m.profissionalId),
          blipQueueId: String(m.blipQueueId ?? '').trim(),
          itsmUserId: String(m.itsmUserId ?? '').trim(),
          discordWebhookUrl: String(m.discordWebhookUrl ?? '').trim(),
          profissionalNome: String(m.profissionalNome ?? '').trim(),
          ignoreAutoSchedule: !m.isActive,
        };
      });

      // 2. Save doctor configs (GerAcesso credentials + Custom Scheduling Rules + Google Review URL + isActive)
      const configPayloads = mappings
        .filter((m) => m.profissionalId && !isNaN(Number(m.profissionalId)))
        .map((m) => {
          return {
            feegowProfissionalId: Number(m.profissionalId),
            doctorName: String(m.profissionalNome ?? '').trim(),
            gerAcessoMatricula: String(m.gerAcessoMatricula ?? '').trim(),
            gerAcessoCpf: String(m.gerAcessoCpf ?? '').replaceAll(/\D/g, '').trim(),
            blipQueueId: String(m.blipQueueId ?? '').trim(),
            blipQueueName: blipQueues.find((q) => q.id === m.blipQueueId)?.name || '',
            displayTimeOffsetMinutes: Number(m.displayTimeOffsetMinutes ?? 0),
            advanceNoticeDays: Number(m.advanceNoticeDays ?? 1),
            googleReviewUrl: String(m.googleReviewUrl ?? '').trim(),
            isActive: Boolean(m.isActive),
          };
        });

      // Fire both save calls
      const [mappingResp] = await Promise.all([
        syncMappings(mappingPayload),
        ...configPayloads.map((cfg) => saveConfig(cfg)),
      ]);

      const status = mappingResp?.status ?? 0;
      if (status >= 200 && status < 300) {
        toast.success('Todos os mapeamentos e status salvos com sucesso no banco!');
        void loadData(); // Reload from DB to confirm values
      } else {
        const fakeError = { response: { data: mappingResp?.data } } as unknown;
        const reason = getApiErrorMessage(fakeError, mappingResp?.data?.reason ?? mappingResp?.statusText ?? `HTTP ${status}`);
        toast.error(reason);
      }
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao salvar mapeamentos de médicos.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="w-full rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
      {/* Header Principal */}
      <header className="border-b border-slate-100 px-6 py-5 bg-gradient-to-r from-white via-slate-50/50 to-white">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-slate-900">Mapeamento de Médicos e Confirmações Automáticas</h2>
              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-700 border border-blue-200">
                <ShieldCheck size={13} className="text-blue-600" />
                Modo Dinâmico (Banco de Dados)
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Ative ou desative o envio automático de mensagens para cada médico com apenas 1 clique no interruptor.
              Configure também filas do Blip, credenciais de catraca e horários.
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => {
                void handleSyncData();
              }}
              disabled={syncingData || loading}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-all hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
              title="Recarrega a lista de profissionais da Feegow e configurações do banco"
            >
              <RefreshCw size={15} className={syncingData ? 'animate-spin text-brand-primary' : 'text-slate-500'} />
              {syncingData ? 'Sincronizando...' : 'Recarregar'}
            </button>

            <button
              type="button"
              onClick={() => {
                void handleSave();
              }}
              disabled={saving || loading || !hasChanges}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
              {saving ? 'Salvando...' : 'Salvar Mapeamentos'}
            </button>
          </div>
        </div>
      </header>

      {/* Cards de Métricas Rápidas */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 px-6 py-4 bg-slate-50/60 border-b border-slate-100">
        <div className="flex items-center gap-3.5 bg-white p-3.5 rounded-xl border border-slate-200/80 shadow-xs">
          <div className="p-2.5 rounded-lg bg-indigo-50 text-indigo-600">
            <Users size={20} />
          </div>
          <div>
            <p className="text-[11px] font-medium text-slate-500 uppercase tracking-wider">Total de Médicos</p>
            <p className="text-xl font-bold text-slate-900">{totalCount}</p>
          </div>
        </div>

        <div className="flex items-center gap-3.5 bg-white p-3.5 rounded-xl border border-emerald-200/80 shadow-xs">
          <div className="p-2.5 rounded-lg bg-emerald-50 text-emerald-600">
            <UserCheck size={20} />
          </div>
          <div>
            <p className="text-[11px] font-medium text-emerald-700 uppercase tracking-wider">Confirmação Ativa</p>
            <div className="flex items-baseline gap-2">
              <p className="text-xl font-bold text-emerald-700">{activeCount}</p>
              <span className="text-[11px] font-medium text-emerald-600">recebendo automações</span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3.5 bg-white p-3.5 rounded-xl border border-slate-200/80 shadow-xs">
          <div className="p-2.5 rounded-lg bg-slate-100 text-slate-600">
            <UserX size={20} />
          </div>
          <div>
            <p className="text-[11px] font-medium text-slate-500 uppercase tracking-wider">Desativados / Pausados</p>
            <div className="flex items-baseline gap-2">
              <p className="text-xl font-bold text-slate-700">{inactiveCount}</p>
              <span className="text-[11px] font-medium text-slate-400">mensagens bloqueadas</span>
            </div>
          </div>
        </div>
      </div>

      {/* Banner / Card do Link Padrão Google Review */}
      <div className="mx-6 my-3 rounded-xl border border-amber-200 bg-amber-50/70 p-3.5 text-xs text-amber-900 shadow-xs flex items-start gap-3">
        <Info size={17} className="text-amber-600 shrink-0 mt-0.5" />
        <div>
          <h4 className="font-bold text-amber-900">Link Padrão da Clínica para Avaliações no Google</h4>
          <p className="mt-0.5 text-amber-800">
            Médicos sem link personalizado preenchido utilizarão o link padrão da clínica:
            <code className="ml-1.5 font-mono font-bold text-amber-950 bg-amber-100/80 px-1.5 py-0.5 rounded border border-amber-200 select-all">
              https://share.google/jrskH337hFK5Mn3WP
            </code>
          </p>
        </div>
      </div>

      {/* Barra de Filtros e Busca */}
      <div className="flex flex-col md:flex-row items-center justify-between border-b border-slate-100 bg-slate-50/40 px-6 py-3 gap-4">
        {/* Campo de Busca */}
        <div className="relative w-full md:w-80">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Buscar médico por nome ou ID..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-slate-200 bg-white text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary focus:border-brand-primary shadow-xs"
          />
          {searchTerm ? (
            <button
              type="button"
              onClick={() => setSearchTerm('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-xs font-bold"
            >
              ×
            </button>
          ) : null}
        </div>

        {/* Abas / Filtros de Status */}
        <div className="flex items-center gap-1 bg-slate-200/70 p-1 rounded-xl">
          <button
            type="button"
            onClick={() => setFilterStatus('ALL')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              filterStatus === 'ALL'
                ? 'bg-white text-slate-800 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Todos ({totalCount})
          </button>
          <button
            type="button"
            onClick={() => setFilterStatus('ACTIVE')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              filterStatus === 'ACTIVE'
                ? 'bg-white text-emerald-700 shadow-xs'
                : 'text-slate-600 hover:text-emerald-700'
            }`}
          >
            ✅ Ativos ({activeCount})
          </button>
          <button
            type="button"
            onClick={() => setFilterStatus('INACTIVE')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              filterStatus === 'INACTIVE'
                ? 'bg-white text-slate-700 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            🚫 Desativados ({inactiveCount})
          </button>
        </div>
      </div>

      {missingIdCount > 0 ? (
        <div className="px-6 py-2 bg-rose-50 border-b border-rose-100 text-xs text-rose-700 font-medium">
          Atenção: Há {missingIdCount} profissional(is) sem ID Feegow. Essas linhas não podem ser salvas.
        </div>
      ) : null}

      {/* Tabela de Mapeamento */}
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 w-16">ID</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 min-w-[220px]">Médico Feegow</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 w-44">Confirmação Automática</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 min-w-[200px]">Fila Blip</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 w-36">CPF Catraca</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 w-32">Matrícula Catraca</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 w-32">Ajuste Horário (min)</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 w-36">Antecedência (dias)</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 min-w-[220px]">Link Google Review</th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={9} className="px-4 py-12 text-center text-sm text-slate-400">
                  <div className="flex flex-col items-center justify-center gap-2">
                    <Loader2 size={24} className="animate-spin text-brand-primary" />
                    <span>Carregando médicos da Feegow e configurações...</span>
                  </div>
                </td>
              </tr>
            ) : filteredMappings.length === 0 ? (
              <tr>
                <td colSpan={9} className="px-4 py-12 text-center text-sm text-slate-400">
                  Nenhum médico encontrado com os filtros atuais.
                </td>
              </tr>
            ) : (
              filteredMappings.map((row, idx) => {
                const profissionalId = String(row.profissionalId ?? '').trim();
                const professional = profissionalId ? professionalsById.get(profissionalId) : undefined;
                const feegowName = professional?.name?.trim() ?? '';
                const resolvedFeegowName = feegowName
                  ? feegowName
                  : profissionalId
                    ? `Sem nome (ID ${profissionalId})`
                    : 'ID ausente';
                const displayName = row.profissionalNome || resolvedFeegowName;
                const isMissingId = !profissionalId;
                const isTogglingThisRow = togglingId === profissionalId;

                const rowOptions = [
                  { id: '', name: 'Nenhuma fila selecionada' },
                  { id: 'inactive', name: '🚫 Inativo (Ocultar/Duplicado)' },
                  ...blipQueues.map((q) => ({ id: q.id, name: q.name })),
                ];

                return (
                  <tr
                    key={`${row.profissionalId}-${idx}`}
                    className={`hover:bg-slate-50/90 transition-colors ${
                      !row.isActive ? 'bg-slate-50/40 text-slate-500' : ''
                    }`}
                  >
                    {/* 1. ID */}
                    <td className={`px-4 py-3 align-middle font-mono text-xs ${isMissingId ? 'text-rose-600' : 'text-slate-700 font-semibold'}`}>
                      {isMissingId ? 'ID ausente' : profissionalId}
                    </td>

                    {/* 2. Nome Feegow */}
                    <td className="px-4 py-3 align-middle">
                      <div className="flex items-center gap-2">
                        <span className={`font-semibold ${row.isActive ? 'text-slate-900' : 'text-slate-500'}`}>
                          {displayName}
                        </span>
                        {!row.isActive ? (
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-600 border border-slate-200">
                            Pausado
                          </span>
                        ) : null}
                      </div>
                    </td>

                    {/* 3. Confirmação Automática (Toggle Switch) */}
                    <td className="px-4 py-3 align-middle">
                      <div className="flex items-center gap-2.5">
                        <button
                          type="button"
                          role="switch"
                          aria-checked={row.isActive}
                          disabled={isTogglingThisRow || isMissingId}
                          onClick={() => {
                            void handleToggleActive(profissionalId, row.isActive, displayName);
                          }}
                          className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-brand-primary focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50 ${
                            row.isActive ? 'bg-emerald-500' : 'bg-slate-300 hover:bg-slate-400'
                          }`}
                          title={row.isActive ? 'Clique para desativar confirmação automática' : 'Clique para ativar confirmação automática'}
                        >
                          <span
                            aria-hidden="true"
                            className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-md ring-0 transition duration-200 ease-in-out ${
                              row.isActive ? 'translate-x-5' : 'translate-x-0'
                            }`}
                          />
                        </button>

                        <div className="flex items-center gap-1 min-w-[75px]">
                          {isTogglingThisRow ? (
                            <Loader2 size={13} className="animate-spin text-brand-primary" />
                          ) : row.isActive ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200/80">
                              ✓ Ativo
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold bg-slate-100 text-slate-600 border border-slate-200/80">
                              Desativado
                            </span>
                          )}
                        </div>
                      </div>
                    </td>

                    {/* 4. Fila Blip */}
                    <td className="px-4 py-3 align-middle min-w-[200px]">
                      <SearchableDropdown
                        options={rowOptions}
                        value={row.blipQueueId || ''}
                        onChange={(val) => updateField(row.profissionalId, 'blipQueueId', val)}
                        placeholder="Selecionar fila do Blip"
                        disabled={!hasBlipQueues}
                      />
                    </td>

                    {/* 5. CPF Catraca */}
                    <td className="px-4 py-3 align-middle w-36">
                      <input
                        type="text"
                        maxLength={14}
                        placeholder="CPF"
                        value={formatCPF(row.gerAcessoCpf || '')}
                        onChange={(e) => updateField(row.profissionalId, 'gerAcessoCpf', e.target.value.replace(/\D/g, '').slice(0, 11))}
                        className="w-full rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-primary"
                      />
                    </td>

                    {/* 6. Matrícula Catraca */}
                    <td className="px-4 py-3 align-middle w-32">
                      <input
                        type="text"
                        placeholder="Matrícula"
                        value={row.gerAcessoMatricula || ''}
                        onChange={(e) => updateField(row.profissionalId, 'gerAcessoMatricula', e.target.value)}
                        className="w-full rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-primary"
                      />
                    </td>

                    {/* 7. Ajuste Horário */}
                    <td className="px-4 py-3 align-middle w-32">
                      <input
                        type="number"
                        step={5}
                        placeholder="0 min"
                        title="Deslocamento em minutos para a mensagem. Ex: -10 envia o horário 10 min antes."
                        value={row.displayTimeOffsetMinutes ?? 0}
                        onChange={(e) => updateField(row.profissionalId, 'displayTimeOffsetMinutes', Number(e.target.value))}
                        className="w-full rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-primary"
                      />
                    </td>

                    {/* 8. Antecedência */}
                    <td className="px-4 py-3 align-middle w-36">
                      <select
                        title="Antecedência de busca/envio em dias. Ex: 2 dias para confirmar procedimentos de sexta na quarta e de sábado na quinta."
                        value={row.advanceNoticeDays ?? 1}
                        onChange={(e) => updateField(row.profissionalId, 'advanceNoticeDays', Number(e.target.value))}
                        className="w-full rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-primary"
                      >
                        <option value={1}>1 dia (Padrão: D+1)</option>
                        <option value={2}>2 dias (Quarta ➔ Sexta | Quinta ➔ Sábado)</option>
                        <option value={3}>3 dias</option>
                        <option value={4}>4 dias</option>
                      </select>
                    </td>

                    {/* 9. Link Google Review */}
                    <td className="px-4 py-3 align-middle min-w-[220px]">
                      <input
                        type="text"
                        placeholder="https://share.google/..."
                        title="URL completa do Google Review ou código hash da avaliação"
                        value={row.googleReviewUrl || ''}
                        onChange={(e) => updateField(row.profissionalId, 'googleReviewUrl', e.target.value)}
                        className="w-full rounded-lg border border-slate-200 px-2.5 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-primary"
                      />
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
