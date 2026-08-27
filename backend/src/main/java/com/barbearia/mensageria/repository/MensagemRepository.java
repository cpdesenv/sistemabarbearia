package com.barbearia.mensageria.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.DirecaoMensagem;
import com.barbearia.mensageria.domain.Mensagem;
import com.barbearia.mensageria.domain.StatusMensagem;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    boolean existsByWaMessageId(String waMessageId);

    List<Mensagem> findByConversaOrderByCriadoEmAsc(Conversa conversa);

    List<Mensagem> findByDirecaoAndStatusAndAtualizadoEmLessThanEqual(DirecaoMensagem direcao, StatusMensagem status,
            Instant limite);
}
