package com.barbearia.fiscal.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Dados ja resolvidos (snapshot) necessarios para emitir o comprovante —
 * o {@link FiscalGateway} nao consulta banco nem conhece entidades JPA,
 * so recebe o que precisa para montar o documento.
 */
public record DadosComprovante(
        long numero,
        Instant emitidoEm,
        String barbeariaNome,
        String barbeariaCnpj,
        String barbeariaEndereco,
        String barbeariaTelefone,
        String clienteNome,
        String clienteTelefone,
        String profissionalNome,
        List<DadosComprovanteItem> itens,
        BigDecimal subtotal,
        BigDecimal descontoValor,
        BigDecimal valorTotal,
        String formaPagamento) {
}
