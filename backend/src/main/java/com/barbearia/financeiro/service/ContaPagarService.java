package com.barbearia.financeiro.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.financeiro.domain.ContaPagar;
import com.barbearia.financeiro.domain.StatusContaPagar;
import com.barbearia.financeiro.dto.CancelarContaRequest;
import com.barbearia.financeiro.dto.ContaPagarDto;
import com.barbearia.financeiro.dto.CriarContaPagarRequest;
import com.barbearia.financeiro.repository.ContaPagarRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class ContaPagarService {

    private final ContaPagarRepository contaPagarRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<ContaPagarDto> listar(StatusContaPagar status) {
        List<ContaPagar> contas = status != null
                ? contaPagarRepository.findByStatusOrderByDataVencimento(status)
                : contaPagarRepository.findAllByOrderByDataVencimento();
        LocalDate hoje = hoje();
        return contas.stream().map(conta -> paraDto(conta, hoje)).toList();
    }

    @Transactional
    public ContaPagarDto criar(CriarContaPagarRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        ContaPagar conta = new ContaPagar();
        conta.setDescricao(requisicao.descricao());
        conta.setValor(requisicao.valor());
        conta.setDataVencimento(requisicao.dataVencimento());
        conta = contaPagarRepository.save(conta);

        auditoriaService.registrar(usuarioId, "CONTA_PAGAR_LANCADA", "conta_pagar", conta.getId(),
                "Conta a pagar '" + conta.getDescricao() + "' de " + conta.getValor() + " lancada, vencimento "
                        + conta.getDataVencimento(),
                httpRequest);

        return paraDto(conta, hoje());
    }

    @Transactional
    public ContaPagarDto marcarPaga(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        ContaPagar conta = buscarPorUuid(uuid);
        exigirPendente(conta);

        conta.setStatus(StatusContaPagar.PAGA);
        conta.setDataPagamento(hoje());
        conta = contaPagarRepository.save(conta);

        auditoriaService.registrar(usuarioId, "CONTA_PAGAR_PAGA", "conta_pagar", conta.getId(),
                "Conta a pagar '" + conta.getDescricao() + "' marcada como paga", httpRequest);

        return paraDto(conta, hoje());
    }

    @Transactional
    public ContaPagarDto cancelar(UUID uuid, CancelarContaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        ContaPagar conta = buscarPorUuid(uuid);
        exigirPendente(conta);

        conta.setStatus(StatusContaPagar.CANCELADA);
        conta = contaPagarRepository.save(conta);

        auditoriaService.registrar(usuarioId, "CONTA_PAGAR_CANCELADA", "conta_pagar", conta.getId(),
                "Conta a pagar '" + conta.getDescricao() + "' cancelada. Motivo: " + requisicao.motivo(),
                httpRequest);

        return paraDto(conta, hoje());
    }

    private void exigirPendente(ContaPagar conta) {
        if (conta.getStatus() != StatusContaPagar.PENDENTE) {
            throw new NegocioException(
                    "Esta conta a pagar esta '" + conta.getStatus() + "' e nao pode mais ser alterada.");
        }
    }

    private LocalDate hoje() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return LocalDate.now(ZoneId.of(barbearia.getFusoHorario()));
    }

    private ContaPagarDto paraDto(ContaPagar conta, LocalDate hoje) {
        boolean vencida = conta.getStatus() == StatusContaPagar.PENDENTE && conta.getDataVencimento().isBefore(hoje);
        return new ContaPagarDto(conta.getUuidPublico(), conta.getDescricao(), conta.getValor(),
                conta.getDataVencimento(), conta.getStatus(), conta.getDataPagamento(), vencida, conta.getCriadoEm(),
                conta.getAtualizadoEm());
    }

    private ContaPagar buscarPorUuid(UUID uuid) {
        return contaPagarRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar nao encontrada."));
    }
}
