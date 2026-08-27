-- =============================================================================
-- Migration V52: Restauração de processing_attempts e Otimização de Índices de FKs
-- =============================================================================

-- 1. Restauração da tabela processing_attempts (utilizada por ContaAzulReceiptRetryPolicy)
CREATE TABLE IF NOT EXISTS processing_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id VARCHAR(120) NOT NULL UNIQUE,
    attempts INTEGER NOT NULL DEFAULT 1,
    last_attempt_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processing_attempts_sale_id 
    ON processing_attempts(sale_id);

-- 2. Índices de Chaves Estrangeiras (FKs) para performance de JOINs e integridade ON DELETE
CREATE INDEX IF NOT EXISTS idx_ticket_assignments_ticket_id 
    ON ticket_assignments(ticket_id);

CREATE INDEX IF NOT EXISTS idx_ticket_assignments_user_id 
    ON ticket_assignments(user_id);

CREATE INDEX IF NOT EXISTS idx_ticket_item_requests_item_id 
    ON ticket_item_requests(item_id);

CREATE INDEX IF NOT EXISTS idx_ticket_relations_ticket_id 
    ON ticket_relations(ticket_id);

CREATE INDEX IF NOT EXISTS idx_ticket_relations_related_id 
    ON ticket_relations(related_ticket_id);

CREATE INDEX IF NOT EXISTS idx_appointment_doctor_mapping_itsm_user 
    ON appointment_doctor_mapping(itsm_user_id);

CREATE INDEX IF NOT EXISTS idx_financial_link_linked_by 
    ON financial_link(linked_by_user_id);

CREATE INDEX IF NOT EXISTS idx_report_schedules_target_user 
    ON report_schedules(target_user_id);

-- 3. Índices de Performance para Auditoria e Notificações em Lote
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at 
    ON audit_logs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id 
    ON audit_logs(user_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_severity 
    ON audit_logs(action, severity);

CREATE INDEX IF NOT EXISTS idx_notification_groups_phone_status 
    ON notification_groups(phone_number, status);
