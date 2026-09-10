-- =============================================================================
-- V55__sync_doctor_active_status_and_defaults.sql
-- Sincroniza o status ativo/inativo dos médicos para confirmação automática.
-- Habilita os 51 médicos clínicos aprovados e desativa os 6 médicos solicitados
-- e agendas administrativas/testes.
-- =============================================================================

-- 1. Garante que todos os 51 médicos clínicos existam em doctor_configurations com is_active = TRUE
INSERT INTO doctor_configurations (feegow_profissional_id, doctor_name, is_active, display_time_offset_minutes, advance_notice_days)
VALUES
    (1, 'GABRIELA CORTIANO', TRUE, 0, 1),
    (2, 'FERNANDO CESAR DE OLIVEIRA', TRUE, 0, 1),
    (3, 'EVANDRO CESAR DE OLIVEIRA', TRUE, 0, 1),
    (5, 'JOSIANE CORREA SCHIAVON', TRUE, 0, 1),
    (6, 'ALISSON VINICIUS EMERIQUE FUCIO', TRUE, 0, 1),
    (7, 'CAROLINA BRUNING', TRUE, 0, 1),
    (8, 'GABRIELA MARTINS BORGES', TRUE, 0, 1),
    (9, 'EDUARDO BISINELLA', TRUE, 0, 1),
    (10, 'CLAUDIO MARCIO ROCHA CAVALCANTI', TRUE, 0, 1),
    (12, 'MAIRA MARINHO LEMOS DE OLIVEIRA', TRUE, 0, 1),
    (13, 'ANIELE CAROLINA SCHELLER', TRUE, 0, 1),
    (14, 'ANDRESSA PACHECO CARSTENS', TRUE, 0, 1),
    (15, 'MARA SUELY VALLADAO', TRUE, 0, 1),
    (17, 'FERNANDO AMARAL BITTENCOURT', TRUE, 0, 1),
    (18, 'MARISTELLA MARCHIOTTO DE ARAUJO', TRUE, 0, 1),
    (19, 'RAQUEL GREGORIO DIAS', TRUE, 0, 1),
    (20, 'JULIANA REIS DE SOUZA', TRUE, 0, 1),
    (21, 'ANDRESSA ROSA BRUNO', TRUE, 0, 1),
    (23, 'RAFAEL AUGUSTO SCHUNEMANN', TRUE, 0, 1),
    (24, 'BRUNA BITTENCOURT', TRUE, 0, 1),
    (26, 'GUILHERME HENRIQUE DE CARVALHO', TRUE, 0, 1),
    (27, 'GUILHERME HENRIQUE DE CARVALHO', TRUE, 0, 1),
    (28, 'EDUARDO MATTOS', TRUE, 0, 1),
    (29, 'RAFAEL AUGUSTO SCHUNEMANN', TRUE, 0, 1),
    (30, 'PAULA PEREIRA DE SOUZA', TRUE, 0, 1),
    (32, 'ROBERTA CRISTINA BORDIN', TRUE, 0, 1),
    (33, 'KARLA SCHUNEMANN DE CARVALHO', TRUE, 0, 1),
    (34, 'MARIA CANDIDA VIEIRA MARINHO DIAS', TRUE, 0, 1),
    (35, 'BRUNA BITTENCOURT', TRUE, 0, 1),
    (36, 'BRUNA BITTENCOURT', TRUE, 0, 1),
    (37, 'JULIA DE LIMA VIEIRA', TRUE, 0, 1),
    (39, 'MARISTELLA MARCHIOTTO DE ARAUJO', TRUE, 0, 1),
    (40, 'MARA SUELY VALLADAO', TRUE, 0, 1),
    (42, 'ISABELLA SANTOS NAKAMURA', TRUE, 0, 1),
    (43, 'RENATA CARNEIRO DA CUNHA', TRUE, 0, 1),
    (44, 'CARLA PATRICIA SCHELBAUER', TRUE, 0, 1),
    (45, 'FERNANDA CAROLINA COELHO DIAS', TRUE, 0, 1),
    (58, 'DANIELE CHRISTINE HEY', TRUE, 0, 1),
    (61, 'LUCIANA MEHL', TRUE, 0, 1),
    (64, 'MAIRA MARINHO LEMOS DE OLIVEIRA', TRUE, 0, 1),
    (69, 'VIVIANE DE PAULA LUZ', TRUE, 0, 1),
    (74, 'ANNA PAULA BORN DE ALMEIDA', TRUE, 0, 1),
    (78, 'ANA CAROLINA DE AGUIAR COSTA', TRUE, 0, 1),
    (80, 'LIA MARA SOUZA SILVA', TRUE, 0, 1),
    (81, 'NATHALIA COELHO CORRÊA', TRUE, 0, 1),
    (82, 'JULIANA VALIATI', TRUE, 0, 1),
    (83, 'LUCIANA MEHL', TRUE, 0, 1),
    (84, 'BEATRIZ CARVALHO DE ANDRADE', TRUE, 0, 1),
    (85, 'JORDANA MAIA GOMES', TRUE, 0, 1),
    (86, 'PRISCILA REGINA MARCHESE DA COSTA', TRUE, 0, 1),
    (90, 'EVELISE TRICHES MARCOLIN', TRUE, 0, 1)
ON CONFLICT (feegow_profissional_id) DO UPDATE
SET is_active = TRUE,
    doctor_name = EXCLUDED.doctor_name
WHERE doctor_configurations.is_active IS NOT TRUE OR doctor_configurations.doctor_name IS NULL;

-- 2. Atualiza appointment_doctor_mapping para os 51 médicos clínicos
UPDATE appointment_doctor_mapping
SET is_active = TRUE,
    ignore_auto_schedule = FALSE
WHERE profissional_id IN (
    '1','2','3','5','6','7','8','9','10','12','13','14','15','17','18','19',
    '20','21','23','24','26','27','28','29','30','32','33','34','35','36',
    '37','39','40','42','43','44','45','58','61','64','69','74','78','80',
    '81','82','83','84','85','86','90'
);

-- 3. Garante que os 6 médicos solicitados para desativação fiquem com is_active = FALSE e ignore_auto_schedule = TRUE
-- 75: Kelly, 4: Marcelo Valladão, 25: Rubens Sirtoli, 46: Anestesistas, 22 e 63: Alexandre Acuña, 76: Thais
INSERT INTO doctor_configurations (feegow_profissional_id, doctor_name, is_active, display_time_offset_minutes, advance_notice_days)
VALUES
    (4, 'MARCELO VALLADÃO', FALSE, 0, 1),
    (22, 'ALEXANDRE BARÃO ACUÑA - DR.', FALSE, 0, 1),
    (25, 'RUBENS SIRTOLI FILHO', FALSE, 0, 1),
    (46, 'ANESTESIOLOGIA', FALSE, 0, 1),
    (63, 'ALEXANDRE BARÃO ACUÑA', FALSE, 0, 1),
    (75, 'KELLY MELINA BRITO COSTA', FALSE, 0, 1),
    (76, 'THAIS FERNANDA SILVESTRI', FALSE, 0, 1)
ON CONFLICT (feegow_profissional_id) DO UPDATE
SET is_active = FALSE;

UPDATE appointment_doctor_mapping
SET is_active = FALSE,
    ignore_auto_schedule = TRUE
WHERE profissional_id IN ('4', '22', '25', '46', '63', '75', '76');

-- 4. Garante que agendas administrativas, de procedimentos/salas e de teste fiquem com is_active = FALSE e ignore_auto_schedule = TRUE
-- 60: Bloqueio, 62: Cirurgia, 65: Biópsia, 66: Colposcopia, 67: Vulvoscopia, 68: Pequenas Cirurgias, 70: Teste Blip, 89: Inovalab
INSERT INTO doctor_configurations (feegow_profissional_id, doctor_name, is_active, display_time_offset_minutes, advance_notice_days)
VALUES
    (60, 'BLOQUEIO / HORÁRIO FECHADO', FALSE, 0, 1),
    (62, 'PROCEDIMENTOS / CIRURGIA', FALSE, 0, 1),
    (65, 'BIÓPSIA DE COLO', FALSE, 0, 1),
    (66, 'COLPOSCOPIA', FALSE, 0, 1),
    (67, 'VULVOSCOPIA', FALSE, 0, 1),
    (68, 'SALA DE PEQUENAS CIRURGIAS', FALSE, 0, 1),
    (70, 'TESTE BLIP', FALSE, 0, 1),
    (89, 'INOVALAB', FALSE, 0, 1)
ON CONFLICT (feegow_profissional_id) DO UPDATE
SET is_active = FALSE;

UPDATE appointment_doctor_mapping
SET is_active = FALSE,
    ignore_auto_schedule = TRUE
WHERE profissional_id IN ('60', '62', '65', '66', '67', '68', '70', '89');
