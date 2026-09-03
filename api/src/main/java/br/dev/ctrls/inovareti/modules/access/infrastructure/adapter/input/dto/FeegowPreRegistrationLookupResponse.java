package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto;

import java.util.List;

/**
 * DTO de resposta para busca prévia no Feegow ao digitar o CPF no formulário de auto-cadastro.
 */
public record FeegowPreRegistrationLookupResponse(
    boolean found,
    String patientName,
    String birthDate,
    String phone,
    List<FeegowAppointmentItemDto> appointments,
    String message
) {
    public record FeegowAppointmentItemDto(
        String appointmentId,
        String doctorName,
        String specialty,
        String date,
        String time,
        String formattedDateTime,
        boolean isToday,
        String location
    ) {}
}
