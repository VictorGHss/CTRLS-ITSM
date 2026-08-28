-- V53__seed_convenios_and_doctor_details.sql
-- Atualiza doctor_configurations com Google Place ID, matricula, CPF e convenios

ALTER TABLE doctor_configurations
    ADD COLUMN IF NOT EXISTS consultation_price VARCHAR(50),
    ADD COLUMN IF NOT EXISTS accepted_convenios TEXT,
    ADD COLUMN IF NOT EXISTS accepted_convenio_ids VARCHAR(255);

INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    26, 'Dra. Vania Gulin', '876252706', '00876252706', NULL, 'R$ 450,00', 'Unimed, Particular', '2, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    46, 'Anestesistas', NULL, NULL, NULL, 'R$ 300,00', 'Unimed, Nossa Saúde, Paraná Clinicas, Particular', '2, 10 , 3, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    20, 'Dra. Liliana Pilatti', '17582046803', '17582046803', 'https://search.google.com/local/writereview?placeid=ChIJeRSNoRwb6JQRXLmBwUg7w_E', 'R$ 450,00', 'Unimed, Particular', '2, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    12, 'Dr. Cesar Oda', '23381892991', '23381892991', 'https://search.google.com/local/writereview?placeid=ChIJuaicHeUb6JQRl3TZFFgKmUI', 'R$ 500,00', 'Unimed, Particular', '2, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    2, 'Dr. Joelson Gulin', '62847996915', '62847996915', NULL, 'R$ 400,00', 'Unimed, São Camilo, Particular', '2, 22, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    15, 'Dr. Daniel Oda', '37', '05876880922', 'https://search.google.com/local/writereview?placeid=ChIJR1Vl21wa6JQRrHFXlVFmsEg', NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    58, 'Dr. Victor Mauro', '2241777960', '02241777960', 'https://search.google.com/local/writereview?placeid=ChIJ0XFz2lwa6JQRptVvp1jo6Y4', 'R$ 450,00', 'Copel, Sanepar, Particular', '5, 19, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    13, 'Dr. Magno Zanellato', '87463490904', '87463490904', 'https://search.google.com/local/writereview?placeid=ChIJe81nM2Ib6JQR68cV4jq_WGE', 'R$ 550,00', 'Unimed', '2', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    18, 'Dr. Bruno Pançan', '5988100996', '05988100996', 'https://search.google.com/local/writereview?placeid=ChIJ7WIdse8b6JQRlj08Im-8A10', 'R$ 400,00', 'Unimed, Nossa Saúde, Paraná Clinicas, São Camilo', '2, 10, 3, 22', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    17, 'Dr. Ricardo Zanetti', '53777069949', '53777069949', 'https://search.google.com/local/writereview?placeid=ChIJKfDO9hkb6JQRioSatPPWgE0', 'R$ 500,00', 'Unimed, Nossa Saúde', '2, 10', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    19, 'Dra. Karen Miyabukuro', '2812473908', '02812473908', NULL, 'R$ 400,00', 'Unimed, Nossa Saúde', '2, 10', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    69, 'Dr. Irineu Zanellato', '2265444944', '02265444944', NULL, 'R$ 450,00', 'Unimed', '2', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    29, 'Dr. Luiz Strack', '2666311937', '02666311937', 'https://search.google.com/local/writereview?placeid=ChIJrWG73XYb6JQRi_Ni10ylyHU', 'R$ 400,00', 'Unimed, São Camilo, Nossa Saúde, Particular', '2, 22, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    23, 'Dra. Ana Paula', '142460745', '00142460745', 'https://search.google.com/local/writereview?placeid=ChIJ5bQ7TkYb6JQRPimXBjWbSYM', 'R$ 400,00', 'Unimed', '2', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    27, 'Dr. Giuliano Campanari', '3222541914', '03222541914', NULL, 'R$ 450,00', 'Unimed', '2', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    22, 'Dr. Alexandre Acuña', '16656691870', '16656691870', 'https://search.google.com/local/writereview?placeid=ChIJFXJz2lwa6JQR5mp-2VacS8c', 'R$ 1.200,00', 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    82, 'Dr. Marcos Marochi', '15', '74868730959', 'https://search.google.com/local/writereview?placeid=ChIJDd6Ri40b6JQRyE-TnaDttUI', NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    24, 'Dra. Cíntia Cenovicz', '74555413920', '74555413920', 'https://search.google.com/local/writereview?placeid=ChIJd4uJ2Vwa6JQRsLFkoMiM60Y', 'R$ 350,00', 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    37, 'Dr. Claudio Solak', '3520496933', '03520496933', 'https://search.google.com/local/writereview?placeid=ChIJsZbOvwUa6JQR9B2EBx_JsUI', NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    5, 'Dr. Carlos Batista', '35018640944', '35018640944', NULL, NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    33, 'Dra. Brenda Aguiar', '3141202974', '03141202974', NULL, NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    32, 'Dra. Isabela Mongruel', '40998312991', '40998312991', NULL, NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    34, 'Dra. Lisa Paula Fernandes', '27076225832', '27076225832', 'https://search.google.com/local/writereview?placeid=ChIJ2ehX3Scb6JQRQi-biatux4Y', NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    36, 'Dra. Tatyellen Dalzotto', '2742680942', '02742680942', NULL, NULL, 'Particular', NULL, true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    21, 'Dr. João Felipe Bueno', '2367830924', NULL, NULL, 'R$ 350,00', 'Unimed, São Camilo', '2, 22', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    14, 'Dr. Marcelo Tessari', '71847332900', '71847332900', 'https://search.google.com/local/writereview?placeid=ChIJa-03bggb6JQRUJ32Ohrq8fY', 'R$ 700,00', 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    3, 'Dr. Carlos Henrique', '14787194860', '14787194860', 'https://search.google.com/local/writereview?placeid=ChIJSZxGTPwb6JQRcbLSyttvCsk', 'R$ 700,00', 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    64, 'Dr. Roberto Kravchychyn', '1234567890', '01234567890', NULL, NULL, 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    9, 'Dr. Marcelo Cenovicz', '74732714900', '74732714900', 'https://search.google.com/local/writereview?placeid=ChIJd4uJ2Vwa6JQRXXQDPeEiMe8', 'R$ 400,00', 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    30, 'Dr. Murilo Cenovicz', '9331290926', '09331290926', 'https://search.google.com/local/writereview?placeid=ChIJd4uJ2Vwa6JQRXXQDPeEiMe8', 'R$ 350,00', 'Paraná Clinicas, MedPrev, Nossa Saúde, Copel, Sanepar, Princesa Assistência, ASPP, Medcar, Pro Saúde, BRF, Solumed, Particular', '3, 25, 10, 5, 19, 17, 13, 15, 16, 26, 31', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    80, 'Dra. Fernanda Cenovicz', '48', NULL, 'https://search.google.com/local/writereview?placeid=ChIJd4uJ2Vwa6JQRXXQDPeEiMe8', 'R$ 350,00', 'Paraná Clinicas, MedPrev, Nossa Saúde, Copel, Sanepar, Princesa Assistência, ASPP, Medcar, Pro Saúde, BRF, Solumed, Particular', '3, 25, 10, 5, 19, 17, 13, 15, 16, 26, 31', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    10, 'Dr. Carlos Miers', '4704275906', '04704275906', 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 400,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular, AMIL', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9, 23', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    39, 'Dr. Cristiano Gatelli', '5007606910', '05007606910', 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 650,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    40, 'Dr. Daniel Cartelli', '3656999937', '03656999937', 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 400,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    42, 'Dr. Franklin Hilgemberg', '3831481970', '03831481970', 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 400,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    43, 'Dr. Luis Felipe', '3756056902', '03756056902', 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 400,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    44, 'Dr. Rafael Pançan', '6137755975', '06137755975', 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 400,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    45, 'Dr. Rodrigo Fávaro', '3536855938', '03536855938', 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 400,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    83, 'Dra. Marina Polydoro', '5509774908', NULL, 'https://search.google.com/local/writereview?placeid=ChIJY9wtc-ob6JQRtSYIa7PpR9A', 'R$ 400,00', 'Unimed, Paraná Clinicas, São Camilo, BRF, Copel, IMASP, Princesa Assistência, Ponta Grossa Ambiental, Siemaco, MedPrev, Pró-Saúde, Cartão do Bem, HelloMed, SindSaude, SAS, Operario, Particular', '2, 3, 22, 26, 5, 29, 17, 28, 7, 25, 16, 35, 39, 37, 8, 24, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    28, 'Dr. Eduardo Mattos', '81116667991', '81116667991', NULL, 'R$ 450,00', 'Unimed, Particular', '2, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    81, 'Dra. Fabíola Moreira', '8020846727', NULL, NULL, 'R$ 300,00', 'Unimed, Particular', '2, 9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    76, 'Dra. Thais Fernanda', '5611401970', '05611401970', 'https://search.google.com/local/writereview?placeid=ChIJX1ufoLwb6JQRKVddq6IdwOc', 'R$ 200,00', 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    75, 'Dra. Kelly Melina', '1005579962', '01005579962', NULL, 'R$ 500,00', 'Particular', '9', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    6, 'Dr. Alisson Fucio', '81581769920', '81581769920', 'https://search.google.com/local/writereview?placeid=ChIJ9_JWozcb6JQRp7iedENtufg', 'R$ 450,00', 'Unimed e Nossa Saúde', '2, 10', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    8, 'Dr. Carlos Koga', '6178369816', '06178369816', 'https://search.google.com/local/writereview?placeid=ChIJ51J6mhUa6JQRNypFNmE2No0', 'R$ 450,00', 'Unimed', '2', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    7, 'Dr. Eduardo Bisinella', '2391656912', '02391656912', 'https://search.google.com/local/writereview?placeid=ChIJ1_w6ENUb6JQRORZRVE3F60Y', 'R$ 450,00', 'Unimed', '2', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
INSERT INTO doctor_configurations (
    feegow_profissional_id, doctor_name, ger_acesso_matricula, ger_acesso_cpf, google_review_url, consultation_price, accepted_convenios, accepted_convenio_ids, is_active
) VALUES (
    90, 'Dr. Ricardo Jeczmionski', '4818308986', '04818308986', 'https://search.google.com/local/writereview?placeid=ChIJd-VZi-wb6JQRKjd4b-nrHU0', 'R$ 450,00', 'Unimed', '2', true
)
ON CONFLICT (feegow_profissional_id) DO UPDATE SET
    ger_acesso_matricula = COALESCE(EXCLUDED.ger_acesso_matricula, doctor_configurations.ger_acesso_matricula),
    ger_acesso_cpf = COALESCE(EXCLUDED.ger_acesso_cpf, doctor_configurations.ger_acesso_cpf),
    google_review_url = COALESCE(EXCLUDED.google_review_url, doctor_configurations.google_review_url),
    consultation_price = EXCLUDED.consultation_price,
    accepted_convenios = EXCLUDED.accepted_convenios,
    accepted_convenio_ids = EXCLUDED.accepted_convenio_ids;
