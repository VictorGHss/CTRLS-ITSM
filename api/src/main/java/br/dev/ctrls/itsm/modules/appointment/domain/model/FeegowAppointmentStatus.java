package br.dev.ctrls.itsm.modules.appointment.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representativo dos status oficiais de agendamento do Feegow ERP.
 */
@Getter
@RequiredArgsConstructor
public enum FeegowAppointmentStatus {

    MARCADO_NAO_CONFIRMADO(1, "Marcado - não confirmado"),
    EM_ATENDIMENTO(2, "Em atendimento"),
    ATENDIDO(3, "Atendido"),
    AGUARDANDO_ATENDIMENTO(4, "Aguardando | Atendimento"),
    CHAMANDO_ATENDIMENTO(5, "Chamando | atendimento"),
    NAO_COMPARECEU(6, "Não compareceu"),
    MARCADO_CONFIRMADO(7, "Marcado - confirmado"),
    DESMARCADO_PACIENTE(11, "Desmarcado pelo paciente"),
    DESMARCADO_OUTRO(12, "Desmarcado - outro / sistema"),
    REMARCADO(15, "Remarcado"),
    DESMARCADO_PROFISSIONAL(16, "Desmarcado pelo profissional"),
    AGUARDANDO_TRIAGEM(101, "Aguardando | Triagem"),
    EM_ATENDIMENTO_TRIAGEM(103, "Em atendimento | Triagem"),
    CHAMANDO_TRIAGEM(105, "Chamando | Triagem"),
    OUTRO(-1, "Outro Status");

    private final int id;
    private final String description;

    /**
     * Converte um ID numérico ou textual em um FeegowAppointmentStatus.
     */
    public static FeegowAppointmentStatus fromId(String statusId) {
        if (statusId == null || statusId.isBlank()) {
            return OUTRO;
        }
        try {
            int idInt = Integer.parseInt(statusId.trim());
            return fromId(idInt);
        } catch (NumberFormatException ex) {
            return OUTRO;
        }
    }

    /**
     * Converte um ID inteiro em um FeegowAppointmentStatus.
     */
    public static FeegowAppointmentStatus fromId(int statusId) {
        for (FeegowAppointmentStatus status : values()) {
            if (status.getId() == statusId) {
                return status;
            }
        }
        return OUTRO;
    }

    /**
     * Indica se o agendamento já está confirmado ou em processo de atendimento físico na clínica.
     */
    public boolean isConfirmedOrPresent() {
        return this == MARCADO_CONFIRMADO
                || this == EM_ATENDIMENTO
                || this == ATENDIDO
                || this == AGUARDANDO_ATENDIMENTO
                || this == CHAMANDO_ATENDIMENTO
                || this == AGUARDANDO_TRIAGEM
                || this == EM_ATENDIMENTO_TRIAGEM
                || this == CHAMANDO_TRIAGEM;
    }

    /**
     * Indica se a consulta foi cancelada, desmarcada ou considerada falta.
     */
    public boolean isCancelledOrMissed() {
        return this == NAO_COMPARECEU
                || this == DESMARCADO_PACIENTE
                || this == DESMARCADO_OUTRO
                || this == DESMARCADO_PROFISSIONAL;
    }

    /**
     * Indica se a consulta é elegível para disparo inicial de confirmação (Marcado ou Remarcado).
     */
    public boolean isEligibleForInitialDispatch() {
        return this == MARCADO_NAO_CONFIRMADO || this == REMARCADO;
    }
}
