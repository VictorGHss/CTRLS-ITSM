package br.dev.ctrls.itsm.modules.access.domain.port.output;

import br.dev.ctrls.itsm.modules.access.domain.model.DoctorAccessData;

/**
 * Porta de saída para obtenção dos metadados de acesso de médicos (matrícula e CPF)
 * para integração com as catracas físicas e API GerAcesso.
 */
public interface DoctorAccessMetadataPort {

    /**
     * Busca os dados de acesso do médico pelo seu ID Feegow/Profissional.
     *
     * @param doctorId Identificador textual do profissional.
     * @return Dados de acesso contendo matrícula e CPF (ou strings vazias caso não configurado).
     */
    DoctorAccessData findByDoctorId(String doctorId);

    /**
     * Busca os dados de acesso do médico pelo nome aproximado (ignora prefixos Dr./Dra.).
     *
     * @param doctorName Nome do médico.
     * @return Dados de acesso contendo matrícula e CPF (ou strings vazias caso não configurado).
     */
    DoctorAccessData findByDoctorName(String doctorName);
}
