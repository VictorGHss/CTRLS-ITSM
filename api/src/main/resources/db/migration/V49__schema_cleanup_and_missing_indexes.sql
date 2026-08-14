-- ==============================================================================
-- Migration V49: Adição de Índices B-Tree em Foreign Keys e Otimização de Performance
-- ==============================================================================

-- 1. Índices para Foreign Keys da Tabela de Chamados (tickets)
CREATE INDEX IF NOT EXISTS idx_tickets_requester ON tickets(requester_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned ON tickets(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_tickets_category ON tickets(category_id);
CREATE INDEX IF NOT EXISTS idx_tickets_requested_item ON tickets(requested_item_id);
CREATE INDEX IF NOT EXISTS idx_tickets_asset ON tickets(asset_id);

-- 2. Índices para Comentários e Anexos de Chamados
CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket ON ticket_comments(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_comments_author ON ticket_comments(author_id);
CREATE INDEX IF NOT EXISTS idx_ticket_attachments_ticket ON ticket_attachments(ticket_id);

-- 3. Índices para Tags de Chamados
CREATE INDEX IF NOT EXISTS idx_ttr_ticket ON ticket_tags_relations(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ttr_tag ON ticket_tags_relations(tag_id);

-- 4. Índices para Usuários e Setores
CREATE INDEX IF NOT EXISTS idx_users_sector ON users(sector_id);

-- 5. Índices para Ativos (assets) e Manutenções
CREATE INDEX IF NOT EXISTS idx_assets_category ON assets(category_id);
CREATE INDEX IF NOT EXISTS idx_asset_maintenances_asset ON asset_maintenances(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_maintenances_technician ON asset_maintenances(technician_id);
CREATE INDEX IF NOT EXISTS idx_asset_users_asset ON asset_users(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_users_user ON asset_users(user_id);

-- 6. Índices para Inventário e Estoque
CREATE INDEX IF NOT EXISTS idx_items_category ON items(item_category_id);
CREATE INDEX IF NOT EXISTS idx_stock_batches_item ON stock_batches(item_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_item ON stock_movements(item_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_recipient_user ON stock_movements(recipient_user_id);

-- 7. Índices para Cofre de Senhas (vault)
CREATE INDEX IF NOT EXISTS idx_vault_items_owner ON vault_items(owner_id);
CREATE INDEX IF NOT EXISTS idx_vault_item_shares_vault_item ON vault_item_shares(vault_item_id);
CREATE INDEX IF NOT EXISTS idx_vault_item_shares_shared_with ON vault_item_shares(shared_with_user_id);

-- 8. Índices para Mapeamentos e Financeiro
CREATE INDEX IF NOT EXISTS idx_doctor_email_mapping_user ON doctor_email_mapping(user_id);
CREATE INDEX IF NOT EXISTS idx_processed_receipts_financial_link ON processed_receipts(financial_link_id);
CREATE INDEX IF NOT EXISTS idx_financial_transactions_ticket ON financial_transactions(ticket_id);
CREATE INDEX IF NOT EXISTS idx_financial_transactions_status ON financial_transactions(status);

-- 9. Índice Composto para Consultas de Sessões por Médico e Data
CREATE INDEX IF NOT EXISTS idx_appointment_sessions_doctor_date 
    ON appointment_sessions(doctor_profissional_id, appointment_date);
