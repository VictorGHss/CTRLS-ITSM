package br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.Optional;

import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentDoctorMappingEntity;

public interface SpringDataAppointmentDoctorMappingRepositoryCustom {
    Optional<AppointmentDoctorMappingEntity> findByProfissionalIdLocked(String profissionalId);
}