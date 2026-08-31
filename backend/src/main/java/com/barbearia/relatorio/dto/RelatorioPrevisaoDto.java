package com.barbearia.relatorio.dto;

import java.math.BigDecimal;
import java.util.List;

import com.barbearia.financeiro.dto.ContaPagarDto;
import com.barbearia.produto.dto.ProdutoDto;

/**
 * Previsao de compromissos (Fase 11): um snapshot do momento atual, nao uma
 * serie historica — por isso nao tem filtro de periodo nem tabela de
 * agregacao diaria, mesmo padrao de {@code FluxoCaixaService} (Fase 5).
 *
 * <p>{@code comissaoTotalMes}/{@code comissaoPorProfissional} sao a comissao
 * ja apurada no mes corrente (ainda em curso) por profissional — o sistema
 * nao tem uma entidade de "pagamento de comissao" com status pago/nao pago,
 * entao isto e' o proximo compromisso mais proximo que da' para calcular a
 * partir do que ja existe (ver {@code RelatorioPrevisaoService}).
 */
public record RelatorioPrevisaoDto(
        BigDecimal comissaoTotalMes,
        List<LinhaFaturamentoDto> comissaoPorProfissional,
        List<ProdutoDto> produtosParaRepor,
        List<ContaPagarDto> contasVencidas) {
}
