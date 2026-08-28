package com.barbearia.mensageria.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.domain.OrigemCadastro;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.ia.service.AgenteAtendimentoService;
import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.DirecaoMensagem;
import com.barbearia.mensageria.domain.Mensagem;
import com.barbearia.mensageria.domain.StatusMensagem;
import com.barbearia.mensageria.domain.TipoMensagem;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.mensageria.repository.MensagemRepository;
import com.barbearia.mensageria.webhook.WebhookPayload;
import com.barbearia.shared.validacao.TelefoneNormalizador;

/**
 * Processa uma mensagem de entrada (webhook real ou simulador). Roda em
 * virtual thread ({@code @Async}, {@code spring.threads.virtual.enabled=true})
 * para que o controller responda 200 imediatamente — nao ha outbox aqui
 * porque nao existe chamada de rede envolvida no recebimento, so o unique
 * constraint em {@code mensagem.wa_message_id} garante a idempotencia.
 */
@Service
@RequiredArgsConstructor
public class MensageriaInboundService {

    private static final Logger log = LoggerFactory.getLogger(MensageriaInboundService.class);

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final ClienteRepository clienteRepository;
    private final AgenteAtendimentoService agenteAtendimentoService;

    @Async
    @Transactional
    public void processarMensagemRecebida(WebhookPayload.MensagemPayload mensagemPayload) {
        if (mensagemRepository.existsByWaMessageId(mensagemPayload.id())) {
            return;
        }

        String telefoneE164;
        try {
            telefoneE164 = TelefoneNormalizador.normalizar(mensagemPayload.from());
        } catch (IllegalArgumentException e) {
            log.warn("Mensagem recebida com telefone invalido, ignorada (waMessageId={})", mensagemPayload.id());
            return;
        }

        Conversa conversa = resolverOuCriarConversa(telefoneE164);

        Mensagem entrada = new Mensagem();
        entrada.setConversa(conversa);
        entrada.setDirecao(DirecaoMensagem.ENTRADA);
        entrada.setTipo(TipoMensagem.TEXTO);
        entrada.setConteudo(mensagemPayload.text().body());
        entrada.setWaMessageId(mensagemPayload.id());
        entrada.setStatus(StatusMensagem.RECEBIDA);
        try {
            mensagemRepository.saveAndFlush(entrada);
        } catch (DataIntegrityViolationException e) {
            // Mesmo waMessageId processado concorrentemente (ex.: reenvio do webhook) — idempotente.
            return;
        }

        conversa.setUltimaMensagemEm(Instant.now());
        conversaRepository.save(conversa);

        agenteAtendimentoService.responder(conversa);
    }

    private Conversa resolverOuCriarConversa(String telefoneE164) {
        return conversaRepository.findByTelefoneE164(telefoneE164)
                .orElseGet(() -> criarConversa(telefoneE164));
    }

    private Conversa criarConversa(String telefoneE164) {
        Cliente cliente = clienteRepository.findByTelefone(telefoneE164)
                .orElseGet(() -> criarClienteRascunho(telefoneE164));

        Conversa conversa = new Conversa();
        conversa.setCliente(cliente);
        conversa.setTelefoneE164(telefoneE164);
        return conversaRepository.save(conversa);
    }

    private Cliente criarClienteRascunho(String telefoneE164) {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente " + telefoneE164);
        cliente.setTelefone(telefoneE164);
        cliente.setOptInWhatsapp(true);
        cliente.setOrigemCadastro(OrigemCadastro.WHATSAPP);
        return clienteRepository.save(cliente);
    }
}
