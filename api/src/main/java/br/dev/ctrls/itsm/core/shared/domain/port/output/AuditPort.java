package br.dev.ctrls.itsm.core.shared.domain.port.output;

public interface AuditPort {

    void record(String module, String action, String details, String traceId);
}
