package com.barbearia.financeiro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.AgendamentoServico;
import com.barbearia.agenda.domain.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.service.AgendamentoService;
import com.barbearia.agenda.service.AvailabilityService;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.financeiro.domain.Comanda;
import com.barbearia.financeiro.domain.ComandaItem;
import com.barbearia.financeiro.domain.FormaPagamento;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.domain.TipoItemComanda;
import com.barbearia.financeiro.dto.AdicionarItemComandaRequest;
import com.barbearia.financeiro.dto.AdicionarItemProdutoComandaRequest;
import com.barbearia.financeiro.dto.AplicarDescontoRequest;
import com.barbearia.financeiro.dto.CaixaDoDiaDto;
import com.barbearia.financeiro.dto.ComandaDto;
import com.barbearia.financeiro.dto.ComandaItemDto;
import com.barbearia.financeiro.dto.DefinirFormaPagamentoRequest;
import com.barbearia.financeiro.dto.EstornarComandaRequest;
import com.barbearia.financeiro.dto.TotalPorFormaPagamentoDto;
import com.barbearia.financeiro.dto.TotalPorProfissionalDto;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.produto.domain.Produto;
import com.barbearia.produto.repository.ProdutoRepository;
import com.barbearia.produto.service.EstoqueService;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.domain.ProfissionalServico;
import com.barbearia.profissional.repository.ProfissionalServicoRepository;
import com.barbearia.servico.domain.Servico;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Comanda de atendimento: itens (servico ou produto), desconto rateado entre
 * os itens, comissao do profissional calculada sobre o valor liquido (apos o
 * rateio do desconto) — so' para itens de servico, produto nao gera comissao
 * nesta fase —, forma de pagamento, fechamento (imutavel) e estorno.
 *
 * <p>Uma comanda e' sempre aberta a partir de um {@link Agendamento}
 * ({@link #abrirParaAgendamento}), que e' quem transiciona o agendamento
 * para EM_ATENDIMENTO (ao abrir) e FINALIZADO (ao fechar a comanda) —
 * reaproveitando as transicoes ja existentes em {@link AgendamentoService}
 * em vez de duplica-las.
 *
 * <p>Itens de produto baixam/devolvem estoque somente no fechamento/estorno
 * da comanda (nunca ao adicionar/remover o item) — ver {@link EstoqueService}.
 */
@Service
@RequiredArgsConstructor
public class ComandaService {

    private final ComandaRepository comandaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoService agendamentoService;
    private final AvailabilityService availabilityService;
    private final ProfissionalServicoRepository profissionalServicoRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueService estoqueService;
    private final BarbeariaRepository barbeariaRepository;
    private final AuditoriaService auditoriaService;

    @Transactional
    public ComandaDto abrirParaAgendamento(UUID agendamentoUuid, Long usuarioId, HttpServletRequest httpRequest) {
        Agendamento agendamento = buscarAgendamento(agendamentoUuid);

        if (agendamento.getStatus() == StatusAgendamento.CONFIRMADO) {
            agendamentoService.iniciarAtendimento(agendamentoUuid, usuarioId, httpRequest);
            return abrirNovaComanda(agendamento, usuarioId, httpRequest);
        }

        if (agendamento.getStatus() == StatusAgendamento.EM_ATENDIMENTO) {
            return comandaRepository.findByAgendamento_UuidPublicoAndStatus(agendamentoUuid, StatusComanda.ABERTA)
                    .map(this::paraDto)
                    .orElseGet(() -> abrirNovaComanda(agendamento, usuarioId, httpRequest));
        }

        if (agendamento.getStatus() == StatusAgendamento.FINALIZADO) {
            List<Comanda> historico = comandaRepository
                    .findByAgendamento_UuidPublicoOrderByCriadoEmDesc(agendamentoUuid);
            if (!historico.isEmpty() && historico.get(0).getStatus() == StatusComanda.FECHADA) {
                throw new NegocioException(
                        "Este agendamento ja possui uma comanda fechada. Estorne-a antes de abrir uma nova.");
            }
            return abrirNovaComanda(agendamento, usuarioId, httpRequest);
        }

        throw new NegocioException(
                "Nao e possivel abrir uma comanda para um agendamento com status '" + agendamento.getStatus()
                        + "'.");
    }

    @Transactional(readOnly = true)
    public ComandaDto obter(UUID uuid) {
        return paraDto(buscarPorUuid(uuid));
    }

    @Transactional
    public ComandaDto adicionarItemServico(UUID comandaUuid, AdicionarItemComandaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Comanda comanda = buscarPorUuid(comandaUuid);
        exigirAberta(comanda);

        Servico servico = availabilityService.resolverServicosAtivos(List.of(requisicao.servicoUuid())).get(0);
        int quantidade = requisicao.quantidadeOuPadrao();

        ComandaItem item = new ComandaItem(servico, servico.getNome(), servico.getPreco());
        item.setQuantidade(quantidade);
        item.setValorBruto(servico.getPreco().multiply(BigDecimal.valueOf(quantidade)));
        item.setValorLiquido(item.getValorBruto());
        comanda.adicionarItem(item);

        recalcularTotais(comanda);
        comanda = comandaRepository.save(comanda);

        auditoriaService.registrar(usuarioId, "COMANDA_ITEM_ADICIONADO", "comanda", comanda.getId(),
                "Item '" + servico.getNome() + "' adicionado a comanda", httpRequest);

        return paraDto(comanda);
    }

    @Transactional
    public ComandaDto adicionarItemProduto(UUID comandaUuid, AdicionarItemProdutoComandaRequest requisicao,
            Long usuarioId, HttpServletRequest httpRequest) {
        Comanda comanda = buscarPorUuid(comandaUuid);
        exigirAberta(comanda);

        Produto produto = produtoRepository.findByUuidPublico(requisicao.produtoUuid())
                .filter(Produto::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
        int quantidade = requisicao.quantidadeOuPadrao();

        if (quantidade > produto.getEstoqueAtual()) {
            throw new NegocioException("Estoque insuficiente de '" + produto.getNome() + "' (disponivel: "
                    + produto.getEstoqueAtual() + ").");
        }

        ComandaItem item = new ComandaItem(produto, produto.getNome(), produto.getPrecoVenda());
        item.setQuantidade(quantidade);
        item.setValorBruto(produto.getPrecoVenda().multiply(BigDecimal.valueOf(quantidade)));
        item.setValorLiquido(item.getValorBruto());
        comanda.adicionarItem(item);

        recalcularTotais(comanda);
        comanda = comandaRepository.save(comanda);

        auditoriaService.registrar(usuarioId, "COMANDA_ITEM_PRODUTO_ADICIONADO", "comanda", comanda.getId(),
                "Produto '" + produto.getNome() + "' (x" + quantidade + ") adicionado a comanda", httpRequest);

        return paraDto(comanda);
    }

    @Transactional
    public ComandaDto removerItem(UUID comandaUuid, UUID itemUuid, Long usuarioId, HttpServletRequest httpRequest) {
        Comanda comanda = buscarPorUuid(comandaUuid);
        exigirAberta(comanda);

        ComandaItem item = comanda.getItens().stream()
                .filter(i -> i.getUuidPublico().equals(itemUuid))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item nao encontrado nesta comanda."));
        comanda.removerItem(item);

        recalcularTotais(comanda);
        comanda = comandaRepository.save(comanda);

        auditoriaService.registrar(usuarioId, "COMANDA_ITEM_REMOVIDO", "comanda", comanda.getId(),
                "Item '" + item.getDescricao() + "' removido da comanda", httpRequest);

        return paraDto(comanda);
    }

    @Transactional
    public ComandaDto aplicarDesconto(UUID comandaUuid, AplicarDescontoRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Comanda comanda = buscarPorUuid(comandaUuid);
        exigirAberta(comanda);

        BigDecimal valor = requisicao.valor();
        if (valor.compareTo(comanda.getSubtotal()) > 0) {
            throw new NegocioException("O desconto nao pode ser maior que o subtotal da comanda.");
        }
        boolean temDesconto = valor.compareTo(BigDecimal.ZERO) > 0;
        if (temDesconto && (requisicao.motivo() == null || requisicao.motivo().isBlank())) {
            throw new NegocioException("Informe o motivo do desconto.");
        }

        comanda.setDescontoValor(valor);
        comanda.setDescontoMotivo(temDesconto ? requisicao.motivo() : null);

        recalcularTotais(comanda);
        comanda = comandaRepository.save(comanda);

        auditoriaService.registrar(usuarioId, "COMANDA_DESCONTO_APLICADO", "comanda", comanda.getId(),
                "Desconto de " + valor + " aplicado" + (temDesconto ? ". Motivo: " + requisicao.motivo() : ""),
                httpRequest);

        return paraDto(comanda);
    }

    @Transactional
    public ComandaDto definirFormaPagamento(UUID comandaUuid, DefinirFormaPagamentoRequest requisicao,
            Long usuarioId, HttpServletRequest httpRequest) {
        Comanda comanda = buscarPorUuid(comandaUuid);
        exigirAberta(comanda);

        comanda.setFormaPagamento(requisicao.formaPagamento());
        comanda = comandaRepository.save(comanda);

        auditoriaService.registrar(usuarioId, "COMANDA_FORMA_PAGAMENTO_DEFINIDA", "comanda", comanda.getId(),
                "Forma de pagamento definida: " + requisicao.formaPagamento(), httpRequest);

        return paraDto(comanda);
    }

    @Transactional
    public ComandaDto fechar(UUID comandaUuid, Long usuarioId, HttpServletRequest httpRequest) {
        Comanda comanda = buscarPorUuid(comandaUuid);
        exigirAberta(comanda);

        if (comanda.getItens().isEmpty()) {
            throw new NegocioException("Adicione ao menos um item antes de fechar a comanda.");
        }
        if (comanda.getFormaPagamento() == null) {
            throw new NegocioException("Selecione a forma de pagamento antes de fechar a comanda.");
        }

        for (ComandaItem item : comanda.getItens()) {
            if (item.getTipo() == TipoItemComanda.PRODUTO) {
                estoqueService.baixarPorComanda(item.getProduto(), item.getQuantidade(), comanda.getId(), usuarioId,
                        httpRequest);
            }
        }

        comanda.setStatus(StatusComanda.FECHADA);
        comanda.setFechadaEm(Instant.now());
        comanda.setFechadaPorUsuarioId(usuarioId);
        comanda = comandaRepository.save(comanda);

        agendamentoService.finalizar(comanda.getAgendamento().getUuidPublico(), usuarioId, httpRequest);

        auditoriaService.registrar(usuarioId, "COMANDA_FECHADA", "comanda", comanda.getId(),
                "Comanda fechada. Total: " + comanda.getValorTotal() + ", forma de pagamento: "
                        + comanda.getFormaPagamento(),
                httpRequest);

        return paraDto(comanda);
    }

    @Transactional
    public ComandaDto estornar(UUID comandaUuid, EstornarComandaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Comanda comanda = buscarPorUuid(comandaUuid);
        if (comanda.getStatus() != StatusComanda.FECHADA) {
            throw new NegocioException(
                    "So e possivel estornar uma comanda fechada. Status atual: '" + comanda.getStatus() + "'.");
        }

        comanda.setStatus(StatusComanda.ESTORNADA);
        comanda.setEstornadaEm(Instant.now());
        comanda.setEstornadaPorUsuarioId(usuarioId);
        comanda.setMotivoEstorno(requisicao.motivo());
        comanda = comandaRepository.save(comanda);

        for (ComandaItem item : comanda.getItens()) {
            if (item.getTipo() == TipoItemComanda.PRODUTO) {
                estoqueService.devolverPorComanda(item.getProduto(), item.getQuantidade(), comanda.getId(),
                        usuarioId, httpRequest);
            }
        }

        auditoriaService.registrar(usuarioId, "COMANDA_ESTORNADA", "comanda", comanda.getId(),
                "Comanda estornada. Motivo: " + requisicao.motivo(), httpRequest);

        return paraDto(comanda);
    }

    @Transactional(readOnly = true)
    public CaixaDoDiaDto calcularCaixaDoDia(LocalDate dataOpcional) {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        ZoneId fuso = ZoneId.of(barbearia.getFusoHorario());
        LocalDate data = dataOpcional != null ? dataOpcional : LocalDate.now(fuso);
        Instant inicioDoDia = data.atStartOfDay(fuso).toInstant();
        Instant fimDoDia = data.plusDays(1).atStartOfDay(fuso).toInstant();

        List<Comanda> comandas = comandaRepository.findByStatusAndFechadaEmBetween(StatusComanda.FECHADA,
                inicioDoDia, fimDoDia);

        BigDecimal totalGeral = BigDecimal.ZERO;
        Map<FormaPagamento, BigDecimal> porForma = new EnumMap<>(FormaPagamento.class);
        Map<UUID, AcumuladorProfissional> porProfissional = new LinkedHashMap<>();

        for (Comanda comanda : comandas) {
            totalGeral = totalGeral.add(comanda.getValorTotal());
            porForma.merge(comanda.getFormaPagamento(), comanda.getValorTotal(), BigDecimal::add);

            Profissional profissional = comanda.getAgendamento().getProfissional();
            AcumuladorProfissional acumulado = porProfissional.computeIfAbsent(profissional.getUuidPublico(),
                    uuid -> new AcumuladorProfissional(profissional.getNome()));
            acumulado.faturado = acumulado.faturado.add(comanda.getValorTotal());
            acumulado.comissao = acumulado.comissao.add(comanda.getItens().stream()
                    .map(ComandaItem::getComissaoValor)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        List<TotalPorFormaPagamentoDto> listaFormaPagamento = porForma.entrySet().stream()
                .map(entrada -> new TotalPorFormaPagamentoDto(entrada.getKey(), entrada.getValue()))
                .toList();
        List<TotalPorProfissionalDto> listaProfissional = porProfissional.entrySet().stream()
                .map(entrada -> new TotalPorProfissionalDto(entrada.getKey(), entrada.getValue().nome,
                        entrada.getValue().faturado, entrada.getValue().comissao))
                .toList();

        return new CaixaDoDiaDto(data, totalGeral, listaFormaPagamento, listaProfissional);
    }

    private ComandaDto abrirNovaComanda(Agendamento agendamento, Long usuarioId, HttpServletRequest httpRequest) {
        Comanda comanda = new Comanda();
        comanda.setAgendamento(agendamento);
        for (AgendamentoServico agendamentoServico : agendamento.getServicos()) {
            comanda.adicionarItem(new ComandaItem(agendamentoServico.getServico(),
                    agendamentoServico.getServico().getNome(), agendamentoServico.getPreco()));
        }
        recalcularTotais(comanda);

        try {
            comanda = comandaRepository.saveAndFlush(comanda);
        } catch (DataIntegrityViolationException ex) {
            return comandaRepository
                    .findByAgendamento_UuidPublicoAndStatus(agendamento.getUuidPublico(), StatusComanda.ABERTA)
                    .map(this::paraDto)
                    .orElseThrow(() -> ex);
        }

        auditoriaService.registrar(usuarioId, "COMANDA_ABERTA", "comanda", comanda.getId(),
                "Comanda aberta para o agendamento de '" + agendamento.getCliente().getNome() + "'", httpRequest);

        return paraDto(comanda);
    }

    /**
     * Recalcula subtotal, rateio do desconto por item (servico e produto, os
     * dois entram no rateio proporcional ao valor bruto), comissao por item
     * (somente para itens de servico — produto nao gera comissao nesta fase)
     * e o valor total da comanda. Chamado sempre que a lista de itens ou o
     * desconto mudam, para que a comanda sempre mostre em tempo real quanto o
     * profissional vai receber.
     */
    private void recalcularTotais(Comanda comanda) {
        List<ComandaItem> itens = comanda.getItens();
        BigDecimal subtotal = itens.stream().map(ComandaItem::getValorBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        comanda.setSubtotal(subtotal);

        BigDecimal desconto = comanda.getDescontoValor() == null ? BigDecimal.ZERO : comanda.getDescontoValor();
        if (desconto.compareTo(subtotal) > 0) {
            desconto = subtotal;
        }

        Profissional profissional = comanda.getAgendamento().getProfissional();
        BigDecimal descontoRateadoAcumulado = BigDecimal.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            ComandaItem item = itens.get(i);
            BigDecimal descontoItem;
            if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
                descontoItem = BigDecimal.ZERO;
            } else if (i == itens.size() - 1) {
                descontoItem = desconto.subtract(descontoRateadoAcumulado);
            } else {
                descontoItem = desconto.multiply(item.getValorBruto()).divide(subtotal, 2, RoundingMode.HALF_UP);
            }
            descontoRateadoAcumulado = descontoRateadoAcumulado.add(descontoItem);

            item.setValorDescontoRateado(descontoItem);
            BigDecimal liquido = item.getValorBruto().subtract(descontoItem);
            item.setValorLiquido(liquido);

            if (item.getTipo() == TipoItemComanda.SERVICO) {
                BigDecimal percentual = resolverComissaoPercentual(profissional, item.getServico());
                item.setComissaoPercentualAplicado(percentual);
                item.setComissaoValor(liquido.multiply(percentual).divide(BigDecimal.valueOf(100), 2,
                        RoundingMode.HALF_UP));
            } else {
                item.setComissaoPercentualAplicado(null);
                item.setComissaoValor(null);
            }
        }

        comanda.setValorTotal(subtotal.subtract(desconto));
    }

    private BigDecimal resolverComissaoPercentual(Profissional profissional, Servico servico) {
        return profissionalServicoRepository.findByProfissional(profissional).stream()
                .filter(vinculo -> vinculo.getServico().getId().equals(servico.getId()))
                .map(ProfissionalServico::getComissaoPercentual)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(profissional.getComissaoPercentualPadrao());
    }

    private void exigirAberta(Comanda comanda) {
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new NegocioException(
                    "Esta comanda esta '" + comanda.getStatus() + "' e nao pode mais ser alterada.");
        }
    }

    private ComandaDto paraDto(Comanda comanda) {
        List<ComandaItemDto> itens = comanda.getItens().stream()
                .map(item -> new ComandaItemDto(
                        item.getUuidPublico(),
                        item.getTipo(),
                        item.getServico() != null ? item.getServico().getUuidPublico() : null,
                        item.getProduto() != null ? item.getProduto().getUuidPublico() : null,
                        item.getDescricao(), item.getQuantidade(), item.getValorUnitario(), item.getValorBruto(),
                        item.getValorDescontoRateado(), item.getValorLiquido(), item.getComissaoPercentualAplicado(),
                        item.getComissaoValor()))
                .toList();
        BigDecimal comissaoTotal = comanda.getItens().stream()
                .map(ComandaItem::getComissaoValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Agendamento agendamento = comanda.getAgendamento();
        return new ComandaDto(
                comanda.getUuidPublico(),
                agendamento.getUuidPublico(),
                agendamento.getCliente().getUuidPublico(),
                agendamento.getCliente().getNome(),
                agendamento.getProfissional().getUuidPublico(),
                agendamento.getProfissional().getNome(),
                comanda.getStatus(),
                itens,
                comanda.getDescontoValor(),
                comanda.getDescontoMotivo(),
                comanda.getFormaPagamento(),
                comanda.getSubtotal(),
                comanda.getValorTotal(),
                comissaoTotal,
                comanda.getFechadaEm(),
                comanda.getEstornadaEm(),
                comanda.getMotivoEstorno(),
                comanda.getCriadoEm(),
                comanda.getAtualizadoEm());
    }

    private Comanda buscarPorUuid(UUID uuid) {
        return comandaRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Comanda nao encontrada."));
    }

    private Agendamento buscarAgendamento(UUID uuid) {
        return agendamentoRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento nao encontrado."));
    }

    private static final class AcumuladorProfissional {
        private final String nome;
        private BigDecimal faturado = BigDecimal.ZERO;
        private BigDecimal comissao = BigDecimal.ZERO;

        private AcumuladorProfissional(String nome) {
            this.nome = nome;
        }
    }
}
