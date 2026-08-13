package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.time.LocalDate;
import java.util.List;

/**
 * Porta de Saída do Domínio: AppointmentExternalPort.
 * Interface Java pura que define os métodos focados em obter e atualizar status de agendamentos a partir de sistemas externos.
 */
public interface AppointmentExternalPort {

    /**
     * Busca os agendamentos da Feegow em uma data específica com um status específico.
     *
     * @param date data do agendamento
     * @param statusId ID do status na Feegow
     * @return lista de agendamentos
     */
    List<FeegowAppointment> searchAppointments(LocalDate date, int statusId);

    /**
     * Busca os agendamentos da Feegow em uma data e status específicos para um médico específico.
     *
     * @param date data do agendamento
     * @param statusId ID do status na Feegow
     * @param profissionalId ID do profissional de saúde (opcional)
     * @return lista de agendamentos
     */
    List<FeegowAppointment> searchAppointments(LocalDate date, int statusId, String profissionalId);

    /**
     * Busca as consultas/agendamentos futuros de um paciente específico pelo seu ID na Feegow.
     *
     * @param patientId ID do paciente
     * @return lista de agendamentos futuros
     */
    List<FeegowAppointment> searchPatientAppointments(String patientId);

    /**
     * Consulta os detalhes de um agendamento específico no Feegow ERP pelo seu ID.
     *
     * @param appointmentId ID do agendamento
     * @return FeegowAppointment correspondente ou null se não localizado
     */
    FeegowAppointment findById(String appointmentId);

    /**
     * Atualiza o status de um agendamento na Feegow por meio do identificador (String).
     *
     * @param appointmentId ID do agendamento
     * @param statusId ID do status
     */
    void updateAppointmentStatus(String appointmentId, String statusId);

    /**
     * Atualiza o status de um agendamento na Feegow por meio do identificador e ID numérico.
     *
     * @param appointmentId ID do agendamento
     * @param statusId ID numérico do status
     */
    void updateStatus(String appointmentId, int statusId);

    /**
     * Cancela uma consulta na Feegow enviando o ID e um motivo/observação.
     *
     * @param appointmentId ID do agendamento
     * @param obs observação/motivo do cancelamento
     */
    void cancelAppointment(String appointmentId, String obs);

    /**
     * Busca a lista de bloqueios de agenda cadastrados no Feegow ERP para um período e unidade.
     *
     * @param startDate data inicial do período
     * @param endDate data final do período
     * @param unitId ID da unidade (opcional)
     * @return lista de DTOs de bloqueio
     */
    List<br.dev.ctrls.inovareti.modules.appointment.application.dto.FeegowLockDto> listLocks(LocalDate startDate, LocalDate endDate, Long unitId);
}
