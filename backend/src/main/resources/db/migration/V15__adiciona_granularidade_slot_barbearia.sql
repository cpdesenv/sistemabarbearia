ALTER TABLE barbearia
    ADD COLUMN granularidade_slot_minutos INT NOT NULL DEFAULT 15
        CHECK (granularidade_slot_minutos > 0);

COMMENT ON COLUMN barbearia.granularidade_slot_minutos IS 'Intervalo (em minutos) entre os horarios candidatos oferecidos pelo motor de disponibilidade (Fase 4).';
