package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.AppointmentDoctorMappingEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataAppointmentDoctorMappingRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de Saída que implementa a Porta de Repositório do Domínio 
 * para fazer a ponte com o Spring Data JPA para os mapeamentos de médicos de agendamento.
 * Implementa cache local em memória (Caffeine) para evitar queries repetitivas ao banco a cada mensagem.
 */
@Component
@RequiredArgsConstructor
public class AppointmentDoctorMappingRepositoryAdapter implements AppointmentDoctorMappingRepositoryPort {

    private final SpringDataAppointmentDoctorMappingRepository springDataRepository;

    @Override
    @Cacheable(value = "doctorMappingByProfissionalId", key = "#profissionalId != null ? #profissionalId.trim() : ''")
    public Optional<AppointmentDoctorMapping> findByProfissionalId(String profissionalId) {
        return springDataRepository.findByProfissionalId(profissionalId).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentDoctorMapping> findByProfissionalIdLocked(String profissionalId) {
        return springDataRepository.findByProfissionalIdLocked(profissionalId).map(entity -> entity.toDomain());
    }

    @Override
    public Optional<AppointmentDoctorMapping> findById(UUID id) {
        return springDataRepository.findById(id).map(entity -> entity.toDomain());
    }

    @Override
    public boolean existsById(UUID id) {
        return springDataRepository.existsById(id);
    }

    @Override
    @Cacheable(value = "doctorMappingsList", key = "'all'")
    public List<AppointmentDoctorMapping> findAll() {
        return springDataRepository.findAll().stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = {"doctorMappingByProfissionalId", "doctorMappingsList"}, allEntries = true)
    public AppointmentDoctorMapping save(AppointmentDoctorMapping mapping) {
        AppointmentDoctorMappingEntity entity = AppointmentDoctorMappingEntity.fromDomain(mapping);
        AppointmentDoctorMappingEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    @CacheEvict(value = {"doctorMappingByProfissionalId", "doctorMappingsList"}, allEntries = true)
    public void delete(AppointmentDoctorMapping mapping) {
        springDataRepository.delete(AppointmentDoctorMappingEntity.fromDomain(mapping));
    }

    @Override
    @CacheEvict(value = {"doctorMappingByProfissionalId", "doctorMappingsList"}, allEntries = true)
    public void deleteById(UUID id) {
        springDataRepository.deleteById(id);
    }

    @Override
    @CacheEvict(value = {"doctorMappingByProfissionalId", "doctorMappingsList"}, allEntries = true)
    public void deleteAll(List<AppointmentDoctorMapping> mappings) {
        List<AppointmentDoctorMappingEntity> entities = mappings.stream()
                .map(AppointmentDoctorMappingEntity::fromDomain)
                .collect(Collectors.toList());
        springDataRepository.deleteAll(entities);
    }
}