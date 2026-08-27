package com.barbearia.mensageria.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mensagem de uma conversa, ENTRADA (recebida do cliente) ou SAIDA (enviada
 * pela barbearia, incluindo o eco automatico). {@link #waMessageId} e o id
 * atribuido pelo provedor (ou pelo mock) — unico quando presente, e a base
 * da idempotencia do webhook (ver MensageriaInboundService).
 */
@Entity
@Table(name = "mensagem")
@Getter
@Setter
@NoArgsConstructor
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private Conversa conversa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DirecaoMensagem direcao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMensagem tipo = TipoMensagem.TEXTO;

    @Column(columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "wa_message_id", unique = true)
    private String waMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMensagem status;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
