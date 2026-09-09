package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.domain.model.DoctorAccessData;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.DoctorAccessMetadataPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Componente de domínio responsável por resolver matrícula e CPF de médicos visitados
 * para integração com a GerAcesso API, desacoplado de detalhes de persistência de agendamentos.
 */
@Slf4j
@Component
public class DoctorAccessResolver {

    private final DoctorAccessMetadataPort doctorAccessMetadataPort;

    public DoctorAccessResolver(DoctorAccessMetadataPort doctorAccessMetadataPort) {
        this.doctorAccessMetadataPort = doctorAccessMetadataPort != null ? doctorAccessMetadataPort : new DoctorAccessMetadataPort() {
            @Override
            public DoctorAccessData findByDoctorId(String doctorId) {
                return new DoctorAccessData("", "");
            }

            @Override
            public DoctorAccessData findByDoctorName(String doctorName) {
                return new DoctorAccessData("", "");
            }
        };
    }

    public DoctorAccessData resolveDoctorAccessData(String docId) {
        if (docId == null || docId.isBlank()) {
            return new DoctorAccessData("", "");
        }
        return doctorAccessMetadataPort.findByDoctorId(docId);
    }

    public DoctorAccessData resolveDoctorAccessDataByName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return new DoctorAccessData("", "");
        }
        return doctorAccessMetadataPort.findByDoctorName(doctorName);
    }
}
