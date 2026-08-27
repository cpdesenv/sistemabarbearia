package com.barbearia.cliente.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.service.AgendamentoService;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.dto.AnonimizarClienteRequest;
import com.barbearia.cliente.dto.ClienteDto;
import com.barbearia.cliente.dto.ClienteMapper;
import com.barbearia.cliente.dto.ExportacaoClienteDto;
import com.barbearia.cliente.dto.FichaClienteDto;
import com.barbearia.cliente.dto.SalvarClienteRequest;
import com.barbearia.cliente.exception.ClienteDuplicadoException;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.cliente.repository.ClienteSpecifications;
import com.barbearia.financeiro.service.ComandaService;
import com.barbearia.fiscal.service.ComprovanteService;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;
import com.barbearia.shared.validacao.CpfValidador;
import com.barbearia.shared.validacao.TelefoneNormalizador;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClienteMapper clienteMapper;
    private final AuditoriaService auditoriaService;
    private final AgendamentoService agendamentoService;
    private final ComandaService comandaService;
    private final ComprovanteService comprovanteService;

    @Transactional(readOnly = true)
    public Page<ClienteDto> listar(String busca, Pageable pageable) {
        return clienteRepository.findAll(ClienteSpecifications.comFiltros(busca), pageable)
                .map(clienteMapper::paraDto);
    }

    @Transactional(readOnly = true)
    public ClienteDto obter(UUID uuid) {
        return clienteMapper.paraDto(buscarPorUuid(uuid));
    }

    @Transactional(readOnly = true)
    public FichaClienteDto ficha(UUID uuid) {
        Cliente cliente = buscarPorUuid(uuid);
        return new FichaClienteDto(
                clienteMapper.paraDto(cliente),
                agendamentoService.listarPorCliente(uuid),
                comandaService.listarPorCliente(uuid),
                comprovanteService.listarPorCliente(uuid));
    }

    @Transactional
    public ClienteDto criar(SalvarClienteRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        String telefone = normalizarTelefone(requisicao.telefone());
        clienteRepository.findByTelefone(telefone).ifPresent(existente -> {
            throw new ClienteDuplicadoException(existente);
        });

        Cliente cliente = new Cliente();
        clienteMapper.copiarPara(requisicao, cliente);
        cliente.setTelefone(telefone);
        cliente.setWhatsapp(normalizarTelefoneOpcional(requisicao.whatsapp()));
        cliente.setCpf(normalizarEValidarCpf(requisicao.cpf()));
        aplicarConsentimento(cliente, requisicao.consentimentoLgpd());

        cliente = clienteRepository.save(cliente);

        auditoriaService.registrar(usuarioId, "CLIENTE_CADASTRADO", "cliente", cliente.getId(),
                "Cliente '" + cliente.getNome() + "' cadastrado", httpRequest);

        return clienteMapper.paraDto(cliente);
    }

    @Transactional
    public ClienteDto atualizar(UUID uuid, SalvarClienteRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Cliente cliente = buscarPorUuid(uuid);
        Long clienteId = cliente.getId();

        String telefone = normalizarTelefone(requisicao.telefone());
        clienteRepository.findByTelefone(telefone)
                .filter(existente -> !existente.getId().equals(clienteId))
                .ifPresent(existente -> {
                    throw new ClienteDuplicadoException(existente);
                });

        clienteMapper.copiarPara(requisicao, cliente);
        cliente.setTelefone(telefone);
        cliente.setWhatsapp(normalizarTelefoneOpcional(requisicao.whatsapp()));
        cliente.setCpf(normalizarEValidarCpf(requisicao.cpf()));
        aplicarConsentimento(cliente, requisicao.consentimentoLgpd());

        cliente = clienteRepository.save(cliente);

        auditoriaService.registrar(usuarioId, "CLIENTE_ATUALIZADO", "cliente", cliente.getId(),
                "Cliente '" + cliente.getNome() + "' atualizado", httpRequest);

        return clienteMapper.paraDto(cliente);
    }

    @Transactional(readOnly = true)
    public ExportacaoClienteDto exportarDados(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        Cliente cliente = buscarPorUuid(uuid);

        auditoriaService.registrar(usuarioId, "CLIENTE_DADOS_EXPORTADOS", "cliente", cliente.getId(),
                "Dados do cliente '" + cliente.getNome() + "' exportados (LGPD)", httpRequest);

        return new ExportacaoClienteDto(Instant.now(), clienteMapper.paraDto(cliente));
    }

    @Transactional
    public ClienteDto anonimizar(UUID uuid, AnonimizarClienteRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Cliente cliente = buscarPorUuid(uuid);

        if (cliente.isAnonimizado()) {
            throw new NegocioException("Este cliente ja foi anonimizado.");
        }

        cliente.setNome("Cliente anonimizado");
        cliente.setTelefone(null);
        cliente.setWhatsapp(null);
        cliente.setCpf(null);
        cliente.setEmail(null);
        cliente.setLogradouro(null);
        cliente.setNumero(null);
        cliente.setComplemento(null);
        cliente.setBairro(null);
        cliente.setCidade(null);
        cliente.setUf(null);
        cliente.setCep(null);
        cliente.setDataNascimento(null);
        cliente.setObservacoes(null);
        cliente.setOptInWhatsapp(false);
        cliente.setAnonimizadoEm(Instant.now());

        cliente = clienteRepository.save(cliente);

        auditoriaService.registrar(usuarioId, "CLIENTE_ANONIMIZADO", "cliente", cliente.getId(),
                "Cliente anonimizado a pedido (LGPD). Motivo: " + requisicao.motivo(), httpRequest);

        return clienteMapper.paraDto(cliente);
    }

    private void aplicarConsentimento(Cliente cliente, boolean consentimentoLgpd) {
        if (consentimentoLgpd && !cliente.isConsentimentoLgpd()) {
            cliente.setConsentimentoLgpdEm(Instant.now());
        }
        cliente.setConsentimentoLgpd(consentimentoLgpd);
    }

    private String normalizarTelefone(String bruto) {
        try {
            return TelefoneNormalizador.normalizar(bruto);
        } catch (IllegalArgumentException ex) {
            throw new NegocioException("Telefone invalido.");
        }
    }

    private String normalizarTelefoneOpcional(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        return normalizarTelefone(bruto);
    }

    private String normalizarEValidarCpf(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String cpf = CpfValidador.normalizar(bruto);
        if (!CpfValidador.valido(cpf)) {
            throw new NegocioException("CPF invalido.");
        }
        return cpf;
    }

    private Cliente buscarPorUuid(UUID uuid) {
        return clienteRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado."));
    }
}
