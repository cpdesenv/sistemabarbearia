package com.barbearia.ia.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.ia.domain.UsoLlm;
import com.barbearia.mensageria.domain.Conversa;

public interface UsoLlmRepository extends JpaRepository<UsoLlm, Long> {

    List<UsoLlm> findByConversaOrderByCriadoEmAsc(Conversa conversa);

    @Query("SELECT COALESCE(SUM(u.custoCentavos), 0) FROM UsoLlm u WHERE u.conversa = :conversa")
    BigDecimal somarCustoCentavosDaConversa(@Param("conversa") Conversa conversa);

    @Query("SELECT COALESCE(SUM(u.custoCentavos), 0) FROM UsoLlm u WHERE u.criadoEm >= :desde")
    BigDecimal somarCustoCentavosDesde(@Param("desde") Instant desde);
}
