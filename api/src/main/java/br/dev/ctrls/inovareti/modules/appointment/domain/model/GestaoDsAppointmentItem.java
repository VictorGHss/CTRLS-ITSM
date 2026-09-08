package br.dev.ctrls.inovareti.modules.appointment.domain.model;

/**
 * Modelo de Domínio: Item de Agendamento do ERP Gestão DS.
 * Isolado de anotações e dependências de infraestrutura/JSON.
 */
public record GestaoDsAppointmentItem(
    String appointmentId,
    String appointmentDate,
    String appointmentEndDate,
    Boolean confirmed,
    Boolean canceled,
    String cancellationReason,
    String professionalName,
    String professionalId,
    String procedureName,
    String patientName,
    String patientCpf,
    String unit,
    Boolean firstAttendance
) {
    public boolean isActive() {
        return canceled == null || !canceled;
    }
}
