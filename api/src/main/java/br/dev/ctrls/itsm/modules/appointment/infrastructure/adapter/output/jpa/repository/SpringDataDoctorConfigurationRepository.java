package br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.jpa.entity.DoctorConfigurationEntity;

import java.util.List;
import java.util.Optional;

/**
 * Interface Spring Data JPA para acesso à tabela doctor_configurations.
 */
public interface SpringDataDoctorConfigurationRepository extends JpaRepository<DoctorConfigurationEntity, Long> {
    List<DoctorConfigurationEntity> findByIsActiveTrueOrIsActiveIsNull();
    Optional<DoctorConfigurationEntity> findByContaazulCustomerUuid(String contaazulCustomerUuid);
}
