package com.barbearia.financeiro.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.financeiro.domain.Despesa;
import com.barbearia.financeiro.dto.CriarDespesaRequest;
import com.barbearia.financeiro.dto.DespesaDto;
import com.barbearia.financeiro.repository.DespesaRepository;
import com.barbearia.shared.auditoria.AuditoriaService;

/**
 * Lancamento de despesas avulsas. Nao ha edicao/exclusao nesta fase — o
 * lancamento e' definitivo, assim como uma comanda fechada so' e' corrigida
 * por estorno em outro fluxo.
 */
@Service
@RequiredArgsConstructor
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<DespesaDto> listar(LocalDate dataInicial, LocalDate dataFinal) {
        List<Despesa> despesas = dataInicial != null && dataFinal != null
                ? despesaRepository.findByDataBetweenOrderByDataDesc(dataInicial, dataFinal)
                : despesaRepository.findAllByOrderByDataDesc();
        return despesas.stream().map(this::paraDto).toList();
    }

    @Transactional
    public DespesaDto criar(CriarDespesaRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        Despesa despesa = new Despesa();
        despesa.setData(requisicao.data());
        despesa.setCategoria(requisicao.categoria());
        despesa.setValor(requisicao.valor());
        despesa.setDescricao(requisicao.descricao());
        despesa.setComprovanteUrl(requisicao.comprovanteUrl());
        despesa.setUsuarioId(usuarioId);
        despesa = despesaRepository.save(despesa);

        auditoriaService.registrar(usuarioId, "DESPESA_REGISTRADA", "despesa", despesa.getId(),
                "Despesa de " + despesa.getValor() + " registrada" + (despesa.getCategoria() != null
                        ? " (categoria: " + despesa.getCategoria() + ")" : ""),
                httpRequest);

        return paraDto(despesa);
    }

    private DespesaDto paraDto(Despesa despesa) {
        return new DespesaDto(despesa.getUuidPublico(), despesa.getData(), despesa.getCategoria(),
                despesa.getValor(), despesa.getDescricao(), despesa.getComprovanteUrl(), despesa.getCriadoEm());
    }
}
