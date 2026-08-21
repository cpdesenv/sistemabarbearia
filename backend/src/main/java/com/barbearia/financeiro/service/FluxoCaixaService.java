package com.barbearia.financeiro.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.domain.StatusContaPagar;
import com.barbearia.financeiro.domain.StatusContaReceber;
import com.barbearia.financeiro.dto.FluxoCaixaDto;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.financeiro.repository.ContaPagarRepository;
import com.barbearia.financeiro.repository.ContaReceberRepository;
import com.barbearia.financeiro.repository.DespesaRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Fluxo de caixa = caixa em maos + contas a receber esperadas - contas a
 * pagar vencidas.
 *
 * <p>"Caixa em maos" e' o acumulado historico de todas as comandas FECHADA
 * (nao so' as de hoje — ver {@code ComandaService.calcularCaixaDoDia} para o
 * caixa do dia) menos todas as despesas ja lancadas. "Contas a receber
 * esperadas" e' a soma de toda conta a receber PENDENTE, independente da
 * data de vencimento (e' dinheiro que o caixa espera receber). "Contas a
 * pagar vencidas" e' a soma das contas a pagar PENDENTE cuja data de
 * vencimento ja passou — uma conta a pagar que ainda nao venceu nao reduz o
 * fluxo de caixa projetado, so' entra quando efetivamente atrasada.
 */
@Service
@RequiredArgsConstructor
public class FluxoCaixaService {

    private final ComandaRepository comandaRepository;
    private final DespesaRepository despesaRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final BarbeariaRepository barbeariaRepository;

    @Transactional(readOnly = true)
    public FluxoCaixaDto calcular() {
        BigDecimal totalComandasFechadas = comandaRepository.somarValorTotalPorStatus(StatusComanda.FECHADA);
        BigDecimal totalDespesas = despesaRepository.somarTodasAsDespesas();
        BigDecimal caixaEmMaos = totalComandasFechadas.subtract(totalDespesas);

        BigDecimal contasAReceberEsperadas = contaReceberRepository.somarPorStatus(StatusContaReceber.PENDENTE);

        LocalDate hoje = hoje();
        BigDecimal contasAPagarVencidas = contaPagarRepository.somarPorStatusVencidasAte(StatusContaPagar.PENDENTE,
                hoje);

        BigDecimal fluxoCaixa = caixaEmMaos.add(contasAReceberEsperadas).subtract(contasAPagarVencidas);

        return new FluxoCaixaDto(caixaEmMaos, contasAReceberEsperadas, contasAPagarVencidas, fluxoCaixa);
    }

    private LocalDate hoje() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return LocalDate.now(ZoneId.of(barbearia.getFusoHorario()));
    }
}
