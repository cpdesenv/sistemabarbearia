package com.barbearia.mensageria.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.mensageria.domain.Conversa;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    Optional<Conversa> findByUuidPublico(UUID uuidPublico);

    Optional<Conversa> findByTelefoneE164(String telefoneE164);

    Page<Conversa> findAllByOrderByUltimaMensagemEmDesc(Pageable pageable);
}
