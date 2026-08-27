package com.barbearia.mensageria.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.Mensagem;
import com.barbearia.mensageria.domain.MensagemEnvioOutbox;
import com.barbearia.mensageria.domain.StatusEnvioOutbox;
import com.barbearia.mensageria.domain.StatusMensagem;
import com.barbearia.mensageria.gateway.WhatsAppEnvioException;
import com.barbearia.mensageria.gateway.WhatsAppGateway;
import com.barbearia.mensageria.repository.MensagemEnvioOutboxRepository;
import com.barbearia.mensageria.repository.MensagemRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemEnvioOutboxWorkerTest {

    @Mock
    private MensagemEnvioOutboxRepository outboxRepository;
    @Mock
    private MensagemRepository mensagemRepository;
    @Mock
    private WhatsAppGateway whatsAppGateway;

    private MensagemEnvioOutboxWorker worker;

    @BeforeEach
    void montarWorker() {
        // "self" so e usado por processarPendencias() (nao testado aqui — os testes
        // chamam processarUm(id) diretamente), entao null e suficiente.
        worker = new MensagemEnvioOutboxWorker(outboxRepository, mensagemRepository, whatsAppGateway, null);
    }

    @Test
    void deveEnviarEMarcarOutboxComoConcluido() {
        Mensagem mensagem = mensagemPendente();
        MensagemEnvioOutbox linha = linhaPendente(mensagem);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));
        when(whatsAppGateway.sendMessage(anyString(), anyString())).thenReturn("mock-msg-123");

        worker.processarUm(1L);

        assertThat(mensagem.getWaMessageId()).isEqualTo("mock-msg-123");
        assertThat(mensagem.getStatus()).isEqualTo(StatusMensagem.ENVIADA);
        assertThat(linha.getStatus()).isEqualTo(StatusEnvioOutbox.CONCLUIDO);
    }

    @Test
    void falhaDeveIncrementarTentativasEAgendarBackoffSemDesistir() {
        Mensagem mensagem = mensagemPendente();
        MensagemEnvioOutbox linha = linhaPendente(mensagem);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));
        when(whatsAppGateway.sendMessage(anyString(), anyString()))
                .thenThrow(new WhatsAppEnvioException("Falha de envio simulada."));

        worker.processarUm(1L);

        assertThat(linha.getStatus()).isEqualTo(StatusEnvioOutbox.PENDENTE);
        assertThat(linha.getTentativas()).isEqualTo(1);
        assertThat(linha.getUltimoErro()).isEqualTo("Falha de envio simulada.");
        assertThat(linha.getProximaTentativaEm()).isAfter(Instant.now());
        assertThat(mensagem.getStatus()).isEqualTo(StatusMensagem.PENDENTE);
    }

    @Test
    void deveMarcarFalhaPermanenteEMensagemComoFalhaAposEsgotarTentativas() {
        Mensagem mensagem = mensagemPendente();
        MensagemEnvioOutbox linha = linhaPendente(mensagem);
        linha.setTentativas(7);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));
        when(whatsAppGateway.sendMessage(anyString(), anyString()))
                .thenThrow(new WhatsAppEnvioException("Falha persistente"));

        worker.processarUm(1L);

        assertThat(linha.getTentativas()).isEqualTo(8);
        assertThat(linha.getStatus()).isEqualTo(StatusEnvioOutbox.FALHA_PERMANENTE);
        assertThat(mensagem.getStatus()).isEqualTo(StatusMensagem.FALHA);
    }

    @Test
    void naoDeveReprocessarLinhaQueNaoEstaMaisPendente() {
        Mensagem mensagem = mensagemPendente();
        MensagemEnvioOutbox linha = linhaPendente(mensagem);
        linha.setStatus(StatusEnvioOutbox.CONCLUIDO);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(linha));

        worker.processarUm(1L);

        verify(whatsAppGateway, never()).sendMessage(any(), any());
    }

    private Mensagem mensagemPendente() {
        Conversa conversa = new Conversa();
        conversa.setTelefoneE164("+5519999998888");
        Mensagem mensagem = new Mensagem();
        mensagem.setConversa(conversa);
        mensagem.setStatus(StatusMensagem.PENDENTE);
        mensagem.setConteudo("recebi: oi");
        return mensagem;
    }

    private MensagemEnvioOutbox linhaPendente(Mensagem mensagem) {
        MensagemEnvioOutbox linha = new MensagemEnvioOutbox();
        linha.setId(1L);
        linha.setMensagem(mensagem);
        linha.setStatus(StatusEnvioOutbox.PENDENTE);
        return linha;
    }
}
