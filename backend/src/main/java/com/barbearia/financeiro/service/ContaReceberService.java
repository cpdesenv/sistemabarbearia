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
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.financeiro.domain.ContaReceber;
import com.barbearia.financeiro.domain.StatusContaReceber;
import com.barbearia.financeiro.dto.CancelarContaRequest;
import com.barbearia.financeiro.dto.ContaReceberDto;
import com.barbearia.financeiro.dto.CriarContaReceberRequest;
import com.barbearia.financeiro.repository.ContaReceberRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class ContaReceberService {

    private final ContaReceberRepository contaReceberRepository;
    private final ClienteRepository clienteRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<ContaReceberDto> listar(StatusContaReceber status, UUID clienteUuid) {
        List<ContaReceber> contas;
        if (clienteUuid != null) {
            contas = contaReceberRepository.findByCliente_UuidPublicoOrderByDataVencimento(clienteUuid);
        } else if (status != null) {
            contas = contaReceberRepository.findByStatusOrderByDataVencimento(status);
        } else {
            contas = contaReceberRepository.findAllByOrderByDataVencimento();
        }
        if (clienteUuid != null && status != null) {
            contas = contas.stream().filter(conta -> conta.getStatus() == status).toList();
        }
        LocalDate hoje = hoje();
        return contas.stream().map(conta -> paraDto(conta, hoje)).toList();
    }

    @Transactional
    public ContaReceberDto criar(CriarContaReceberRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        Cliente cliente = clienteRepository.findByUuidPublico(requisicao.clienteUuid())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado."));

        ContaReceber conta = new ContaReceber();
        conta.setCliente(cliente);
        conta.setDescricao(requisicao.descricao());
        conta.setValor(requisicao.valor());
        conta.setDataVencimento(requisicao.dataVencimento());
        conta = contaReceberRepository.save(conta);

        auditoriaService.registrar(usuarioId, "CONTA_RECEBER_LANCADA", "conta_receber", conta.getId(),
                "Conta a receber de " + conta.getValor() + " lancada para '" + cliente.getNome() + "', vencimento "
                        + conta.getDataVencimento(),
                httpRequest);

        return paraDto(conta, hoje());
    }

    @Transactional
    public ContaReceberDto marcarRecebida(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        ContaReceber conta = buscarPorUuid(uuid);
        exigirPendente(conta);

        conta.setStatus(StatusContaReceber.RECEBIDA);
        conta.setDataRecebimento(hoje());
        conta = contaReceberRepository.save(conta);

        auditoriaService.registrar(usuarioId, "CONTA_RECEBER_RECEBIDA", "conta_receber", conta.getId(),
                "Conta a receber de '" + conta.getCliente().getNome() + "' marcada como recebida", httpRequest);

        return paraDto(conta, hoje());
    }

    @Transactional
    public ContaReceberDto cancelar(UUID uuid, CancelarContaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        ContaReceber conta = buscarPorUuid(uuid);
        exigirPendente(conta);

        conta.setStatus(StatusContaReceber.CANCELADA);
        conta = contaReceberRepository.save(conta);

        auditoriaService.registrar(usuarioId, "CONTA_RECEBER_CANCELADA", "conta_receber", conta.getId(),
                "Conta a receber de '" + conta.getCliente().getNome() + "' cancelada. Motivo: " + requisicao.motivo(),
                httpRequest);

        return paraDto(conta, hoje());
    }

    private void exigirPendente(ContaReceber conta) {
        if (conta.getStatus() != StatusContaReceber.PENDENTE) {
            throw new NegocioException(
                    "Esta conta a receber esta '" + conta.getStatus() + "' e nao pode mais ser alterada.");
        }
    }

    private LocalDate hoje() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        return LocalDate.now(ZoneId.of(barbearia.getFusoHorario()));
    }

    private ContaReceberDto paraDto(ContaReceber conta, LocalDate hoje) {
        boolean vencida = conta.getStatus() == StatusContaReceber.PENDENTE
                && conta.getDataVencimento().isBefore(hoje);
        return new ContaReceberDto(conta.getUuidPublico(), conta.getCliente().getUuidPublico(),
                conta.getCliente().getNome(), conta.getDescricao(), conta.getValor(), conta.getDataVencimento(),
                conta.getStatus(), conta.getDataRecebimento(), vencida, conta.getCriadoEm(), conta.getAtualizadoEm());
    }

    private ContaReceber buscarPorUuid(UUID uuid) {
        return contaReceberRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a receber nao encontrada."));
    }
}
