package com.barbearia.fiscal.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.financeiro.domain.Comanda;
import com.barbearia.fiscal.domain.Comprovante;
import com.barbearia.fiscal.domain.StatusComprovante;
import com.barbearia.fiscal.dto.ComprovanteDto;
import com.barbearia.fiscal.email.EmailGateway;
import com.barbearia.fiscal.gateway.DadosComprovante;
import com.barbearia.fiscal.gateway.DadosComprovanteItem;
import com.barbearia.fiscal.gateway.DocumentoFiscal;
import com.barbearia.fiscal.gateway.FiscalGateway;
import com.barbearia.fiscal.repository.ComprovanteRepository;
import com.barbearia.fiscal.storage.ObjectStorageService;
import com.barbearia.shared.exception.NegocioException;

@Service
@RequiredArgsConstructor
public class ComprovanteService {

    private static final Logger log = LoggerFactory.getLogger(ComprovanteService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ComprovanteRepository comprovanteRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final FiscalGateway fiscalGateway;
    private final ObjectStorageService objectStorageService;
    private final EmailGateway emailGateway;

    /**
     * Reserva o numero e cria o registro do comprovante (status PENDENTE).
     * Chamado de dentro de {@code ComandaService#fechar}, propositalmente
     * SEM {@code @Transactional} proprio: precisa rodar dentro da mesma
     * transacao do fechamento da comanda, para que o numero so seja
     * consumido se o fechamento realmente for commitado (zero buracos). A
     * geracao do arquivo em si e feita depois, separadamente, por
     * {@link #gerarArquivoParaComanda} — ver javadoc de {@link Comprovante}.
     */
    public Comprovante reservarParaComanda(Comanda comanda) {
        long numero = reservarProximoNumero();
        Cliente cliente = comanda.getAgendamento().getCliente();

        Comprovante comprovante = new Comprovante();
        comprovante.setComanda(comanda);
        comprovante.setNumero(numero);
        comprovante.setClienteNomeSnapshot(cliente.getNome());
        comprovante.setClienteTelefoneSnapshot(
                cliente.getWhatsapp() != null ? cliente.getWhatsapp() : cliente.getTelefone());
        comprovante.setClienteEmailSnapshot(cliente.getEmail());
        return comprovanteRepository.save(comprovante);
    }

    private long reservarProximoNumero() {
        Long numero = jdbcTemplate.queryForObject(
                "UPDATE numeracao_comprovante SET proximo_numero = proximo_numero + 1 RETURNING proximo_numero - 1",
                Long.class);
        return numero;
    }

    /** Gera o arquivo para uma comanda cujo comprovante ja foi reservado. Chamado apos o fechamento da comanda ja ter sido commitado. */
    @Transactional
    public void gerarArquivoParaComanda(UUID comandaUuid) {
        gerar(buscarPorComanda(comandaUuid));
    }

    @Transactional
    public ComprovanteDto reenviar(UUID comandaUuid) {
        Comprovante comprovante = buscarPorComanda(comandaUuid);
        gerar(comprovante);
        return paraDto(comprovante);
    }

    private void gerar(Comprovante comprovante) {
        try {
            Comanda comanda = comprovante.getComanda();
            Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                    .orElseThrow(() -> new IllegalStateException("Barbearia (registro singleton) nao encontrada."));

            DadosComprovante dados = montarDados(comprovante, comanda, barbearia);
            DocumentoFiscal documento = fiscalGateway.emitirNotaFiscal(dados);
            String chave = "comprovantes/%06d.pdf".formatted(comprovante.getNumero());
            objectStorageService.salvar(chave, documento.conteudo(), "application/pdf");

            comprovante.setChaveArmazenamento(chave);
            comprovante.setStatus(StatusComprovante.DISPONIVEL);
            comprovante.setGeradoEm(Instant.now());
            comprovante.setUltimoErro(null);
            comprovanteRepository.save(comprovante);

            enviarPorEmailSeHouver(comprovante, documento.conteudo());
        } catch (Exception e) {
            log.error("Falha ao gerar/armazenar comprovante numero {}", comprovante.getNumero(), e);
            comprovante.setStatus(StatusComprovante.FALHA);
            comprovante.setUltimoErro(e.getMessage());
            comprovanteRepository.save(comprovante);
        }
    }

    private void enviarPorEmailSeHouver(Comprovante comprovante, byte[] pdf) {
        String email = comprovante.getClienteEmailSnapshot();
        if (email == null || email.isBlank()) {
            return;
        }
        try {
            emailGateway.enviarComprovante(email, comprovante.getClienteNomeSnapshot(), comprovante.getNumero(), pdf);
        } catch (Exception e) {
            log.warn("Falha ao enviar comprovante numero {} por e-mail (PDF ja esta disponivel no painel).",
                    comprovante.getNumero(), e);
        }
    }

    private DadosComprovante montarDados(Comprovante comprovante, Comanda comanda, Barbearia barbearia) {
        List<DadosComprovanteItem> itens = comanda.getItens().stream()
                .map(item -> new DadosComprovanteItem(item.getDescricao(), item.getQuantidade(),
                        item.getValorUnitario(), item.getValorLiquido()))
                .toList();

        return new DadosComprovante(
                comprovante.getNumero(),
                comprovante.getCriadoEm(),
                barbearia.getNome(),
                barbearia.getCnpj(),
                enderecoFormatado(barbearia),
                barbearia.getTelefone(),
                comanda.getAgendamento().getCliente().getNome(),
                comprovante.getClienteTelefoneSnapshot(),
                comanda.getAgendamento().getProfissional().getNome(),
                itens,
                comanda.getSubtotal(),
                comanda.getDescontoValor(),
                comanda.getValorTotal(),
                comanda.getFormaPagamento() != null ? comanda.getFormaPagamento().name() : "-");
    }

    private String enderecoFormatado(Barbearia barbearia) {
        if (barbearia.getLogradouro() == null) {
            return null;
        }
        StringBuilder endereco = new StringBuilder(barbearia.getLogradouro());
        if (barbearia.getNumero() != null) {
            endereco.append(", ").append(barbearia.getNumero());
        }
        if (barbearia.getBairro() != null) {
            endereco.append(" — ").append(barbearia.getBairro());
        }
        if (barbearia.getCidade() != null) {
            endereco.append(", ").append(barbearia.getCidade());
        }
        if (barbearia.getUf() != null) {
            endereco.append("/").append(barbearia.getUf());
        }
        return endereco.toString();
    }

    @Transactional(readOnly = true)
    public byte[] baixar(UUID comandaUuid) {
        Comprovante comprovante = buscarPorComanda(comandaUuid);
        if (comprovante.getStatus() != StatusComprovante.DISPONIVEL) {
            throw new NegocioException(
                    "Comprovante ainda nao esta disponivel (status: " + comprovante.getStatus()
                            + "). Tente reenviar.");
        }
        return objectStorageService.carregar(comprovante.getChaveArmazenamento());
    }

    @Transactional(readOnly = true)
    public ComprovanteDto obterStatus(UUID comandaUuid) {
        return paraDto(buscarPorComanda(comandaUuid));
    }

    private Comprovante buscarPorComanda(UUID comandaUuid) {
        return comprovanteRepository.findByComanda_UuidPublico(comandaUuid)
                .orElseThrow(() -> new NegocioException("Nenhum comprovante encontrado para esta comanda."));
    }

    private ComprovanteDto paraDto(Comprovante comprovante) {
        return new ComprovanteDto(comprovante.getUuidPublico(), comprovante.getNumero(), comprovante.getStatus(),
                comprovante.getGeradoEm());
    }
}
