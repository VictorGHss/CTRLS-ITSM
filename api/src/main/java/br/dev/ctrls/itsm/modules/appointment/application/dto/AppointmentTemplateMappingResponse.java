package br.dev.ctrls.itsm.modules.appointment.application.dto;

public record AppointmentTemplateMappingResponse(
        String templateName,
        Integer placeholderIndex,
        String feegowFieldName) {
}