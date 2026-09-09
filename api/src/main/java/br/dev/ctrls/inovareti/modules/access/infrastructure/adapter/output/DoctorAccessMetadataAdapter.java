package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.DoctorAccessData;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.DoctorAccessMetadataPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador de infraestrutura que conecta a porta de metadados de acesso de médicos
 * ao repositório de configurações de médicos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DoctorAccessMetadataAdapter implements DoctorAccessMetadataPort {

    private final DoctorConfigurationRepository doctorConfigurationRepository;

    @Override
    public DoctorAccessData findByDoctorId(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return new DoctorAccessData("", "");
        }
        try {
            Long docProfId = Long.parseLong(doctorId.trim());
            return doctorConfigurationRepository.findById(docProfId)
                .map(config -> {
                    String mat = config.getGerAcessoMatricula() != null ? config.getGerAcessoMatricula().trim() : "";
                    String cpf = config.getGerAcessoCpf() != null ? config.getGerAcessoCpf().trim() : "";
                    log.info("[CATRACA-MÉDICO] Injetando dados do visitado para o profissional ID: {} (Matricula: {}, CPF: {})", doctorId, mat, cpf);
                    return new DoctorAccessData(mat, cpf);
                })
                .orElseGet(() -> {
                    log.warn("[CATRACA-MÉDICO] Profissional ID: {} não possui metadados GerAcesso configurados na tabela doctor_configurations.", doctorId);
                    return new DoctorAccessData("", "");
                });
        } catch (Exception ex) {
            log.warn("[CATRACA-MÉDICO] Falha ao buscar credenciais do visitado para o profissional ID: {} - {}", doctorId, ex.getMessage());
            return new DoctorAccessData("", "");
        }
    }

    @Override
    public DoctorAccessData findByDoctorName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return new DoctorAccessData("", "");
        }
        try {
            String cleanQuery = doctorName.toLowerCase()
                    .replace("dra.", "")
                    .replace("dr.", "")
                    .trim();

            List<DoctorConfiguration> allConfigs = doctorConfigurationRepository.findAll();
            if (allConfigs != null) {
                for (DoctorConfiguration config : allConfigs) {
                    if (config.getDoctorName() != null && !config.getDoctorName().isBlank()) {
                        String cfgName = config.getDoctorName().toLowerCase()
                                .replace("dra.", "")
                                .replace("dr.", "")
                                .trim();
                        if (cfgName.equalsIgnoreCase(cleanQuery) || cfgName.contains(cleanQuery) || cleanQuery.contains(cfgName)) {
                            String mat = config.getGerAcessoMatricula() != null ? config.getGerAcessoMatricula().trim() : "";
                            String cpf = config.getGerAcessoCpf() != null ? config.getGerAcessoCpf().trim() : "";
                            log.info("[CATRACA-MÉDICO] Injetando dados do visitado por NOME: '{}' -> '{}' (Matricula: {}, CPF: {})", 
                                    doctorName, config.getDoctorName(), mat, cpf);
                            return new DoctorAccessData(mat, cpf);
                        }
                    }
                }
            }
            log.warn("[CATRACA-MÉDICO] Médico '{}' não possui cadastro correspondente em doctor_configurations.", doctorName);
            return new DoctorAccessData("", "");
        } catch (Exception ex) {
            log.warn("[CATRACA-MÉDICO] Falha ao buscar credenciais do visitado por nome '{}': {}", doctorName, ex.getMessage());
            return new DoctorAccessData("", "");
        }
    }
}
