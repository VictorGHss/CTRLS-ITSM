-- =============================================================================
-- V55__sync_doctor_active_status_and_defaults.sql
-- Sincroniza o status ativo/inativo dos médicos para confirmação automática.
-- Habilita os 51 médicos clínicos aprovados e desativa os 6 médicos solicitados
-- e agendas administrativas/testes.
-- =============================================================================

-- 1. Garante que todos os 51 médicos clínicos existam em doctor_configurations com is_active = TRUE
INSERT INTO doctor_configurations (feegow_profissional_id, doctor_name, is_active, display_time_offset_minutes, advance_notice_days)
VALUES
    (1, 'DRA. CLÍNICA GERAL 01', TRUE, 0, 1),
    (2, 'DR. CARDIOLOGISTA 02', TRUE, 0, 1),
    (3, 'DR. PEDIATRA 03', TRUE, 0, 1),
    (5, 'DRA. GINECOLOGISTA 05', TRUE, 0, 1),
    (6, 'DR. UROLOGISTA 06', TRUE, 0, 1),
    (7, 'DRA. DERMATOLOGISTA 07', TRUE, 0, 1),
    (8, 'DRA. OFTALMOLOGISTA 08', TRUE, 0, 1),
    (9, 'DR. ENDOCRINOLOGISTA 09', TRUE, 0, 1),
    (10, 'DR. NEUROLOGISTA 10', TRUE, 0, 1),
    (12, 'DRA. PSIQUIATRA 12', TRUE, 0, 1),
    (13, 'DRA. PNEUMOLOGISTA 13', TRUE, 0, 1),
    (14, 'DRA. OTORRINO 14', TRUE, 0, 1),
    (15, 'DRA. REUMATOLOGISTA 15', TRUE, 0, 1),
    (17, 'DR. ORTOPEDISTA 17', TRUE, 0, 1),
    (18, 'DRA. GASTROENTEROLOGISTA 18', TRUE, 0, 1),
    (19, 'DRA. NUTROLOGA 19', TRUE, 0, 1),
    (20, 'DRA. CLÍNICA GERAL 20', TRUE, 0, 1),
    (21, 'DRA. PEDIATRA 21', TRUE, 0, 1),
    (23, 'DR. CARDIOLOGISTA 23', TRUE, 0, 1),
    (24, 'DRA. GINECOLOGISTA 24', TRUE, 0, 1),
    (26, 'DR. CIRURGIAO VASCULAR 26', TRUE, 0, 1),
    (27, 'DR. CIRURGIAO GERAL 27', TRUE, 0, 1),
    (28, 'DR. ORTOPEDISTA 28', TRUE, 0, 1),
    (29, 'DR. CARDIOLOGISTA 29', TRUE, 0, 1),
    (30, 'DRA. CLÍNICA GERAL 30', TRUE, 0, 1),
    (32, 'DRA. DERMATOLOGISTA 32', TRUE, 0, 1),
    (33, 'DRA. OFTALMOLOGISTA 33', TRUE, 0, 1),
    (34, 'DRA. ENDOCRINOLOGISTA 34', TRUE, 0, 1),
    (35, 'DRA. GINECOLOGISTA 35', TRUE, 0, 1),
    (36, 'DRA. GINECOLOGISTA 36', TRUE, 0, 1),
    (37, 'DRA. PEDIATRA 37', TRUE, 0, 1),
    (39, 'DRA. GASTROENTEROLOGISTA 39', TRUE, 0, 1),
    (40, 'DRA. REUMATOLOGISTA 40', TRUE, 0, 1),
    (41, 'DR. NEUROCIRURGIAO 41', TRUE, 0, 1),
    (42, 'DRA. CLÍNICA GERAL 42', TRUE, 0, 1),
    (43, 'DRA. PEDIATRA 43', TRUE, 0, 1),
    (44, 'DRA. DERMATOLOGISTA 44', TRUE, 0, 1),
    (45, 'DRA. ALERGOLOGISTA 45', TRUE, 0, 1),
    (58, 'DRA. FONOAUDIOLOGA 58', TRUE, 0, 1),
    (61, 'DRA. PSICOLOGA 61', TRUE, 0, 1),
    (64, 'DRA. PSIQUIATRA 64', TRUE, 0, 1),
    (69, 'DRA. FISIOTERAPEUTA 69', TRUE, 0, 1),
    (74, 'DRA. NUTRICIONISTA 74', TRUE, 0, 1),
    (78, 'DRA. ODONTOLOGA 78', TRUE, 0, 1),
    (80, 'DRA. CLÍNICA GERAL 80', TRUE, 0, 1),
    (81, 'DRA. NEFROLOGISTA 81', TRUE, 0, 1),
    (82, 'DRA. INFECTOLOGISTA 82', TRUE, 0, 1),
    (83, 'DRA. PSICOLOGA 83', TRUE, 0, 1),
    (84, 'DRA. BIOMEDICA 84', TRUE, 0, 1),
    (85, 'DRA. TERAPEUTA 85', TRUE, 0, 1),
    (86, 'DRA. CARDIOLOGISTA 86', TRUE, 0, 1),
    (90, 'DRA. GERIATRA 90', TRUE, 0, 1)
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
    '37','39','40','41','42','43','44','45','58','61','64','69','74','78','80',
    '81','82','83','84','85','86','90'
);

-- 3. Garante que os 6 médicos solicitados para desativação fiquem com is_active = FALSE e ignore_auto_schedule = TRUE
INSERT INTO doctor_configurations (feegow_profissional_id, doctor_name, is_active, display_time_offset_minutes, advance_notice_days)
VALUES
    (4, 'DR. PROFISSIONAL 04', FALSE, 0, 1),
    (22, 'DR. PROFISSIONAL 22', FALSE, 0, 1),
    (25, 'DR. PROFISSIONAL 25', FALSE, 0, 1),
    (46, 'ANESTESIOLOGIA (SETOR)', FALSE, 0, 1),
    (63, 'DR. PROFISSIONAL 63', FALSE, 0, 1),
    (75, 'DRA. PROFISSIONAL 75', FALSE, 0, 1),
    (76, 'DRA. PROFISSIONAL 76', FALSE, 0, 1)
ON CONFLICT (feegow_profissional_id) DO UPDATE
SET is_active = FALSE;

UPDATE appointment_doctor_mapping
SET is_active = FALSE,
    ignore_auto_schedule = TRUE
WHERE profissional_id IN ('4', '22', '25', '46', '63', '75', '76');

-- 4. Garante que agendas administrativas, de procedimentos/salas e de teste fiquem com is_active = FALSE e ignore_auto_schedule = TRUE
INSERT INTO doctor_configurations (feegow_profissional_id, doctor_name, is_active, display_time_offset_minutes, advance_notice_days)
VALUES
    (60, 'BLOQUEIO / HORÁRIO FECHADO', FALSE, 0, 1),
    (62, 'PROCEDIMENTOS / CIRURGIA', FALSE, 0, 1),
    (65, 'BIÓPSIA DE COLO', FALSE, 0, 1),
    (66, 'COLPOSCOPIA', FALSE, 0, 1),
    (67, 'VULVOSCOPIA', FALSE, 0, 1),
    (68, 'SALA DE PEQUENAS CIRURGIAS', FALSE, 0, 1),
    (70, 'TESTE AUTOMATIZADO', FALSE, 0, 1),
    (89, 'LABORATÓRIO CLÍNICO', FALSE, 0, 1)
ON CONFLICT (feegow_profissional_id) DO UPDATE
SET is_active = FALSE;

UPDATE appointment_doctor_mapping
SET is_active = FALSE,
    ignore_auto_schedule = TRUE
WHERE profissional_id IN ('60', '62', '65', '66', '67', '68', '70', '89');
