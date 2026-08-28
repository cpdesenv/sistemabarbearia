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

import com.barbearia.cliente.domain.Cliente;

/**
 * Uma conversa por numero de telefone (telefone_e164 e a identidade da
 * conversa, como no proprio WhatsApp) — sempre vinculada a um cliente,
 * criado como rascunho se o telefone ainda nao era conhecido.
 */
@Entity
@Table(name = "conversa")
@Getter
@Setter
@NoArgsConstructor
public class Conversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "telefone_e164", nullable = false, unique = true)
    private String telefoneE164;

    @Column(name = "ultima_mensagem_em")
    private Instant ultimaMensagemEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_atendimento", nullable = false)
    private ModoAtendimento modoAtendimento = ModoAtendimento.IA;

    @Column(name = "turnos_ia", nullable = false)
    private int turnosIa = 0;

    /** Se ultrapassado sem nova mensagem, o proximo turno da IA reinicia o contexto (timeout de 30 min do PRD). */
    @Column(name = "contexto_expira_em")
    private Instant contextoExpiraEm;

    @Column(name = "motivo_escalonamento", columnDefinition = "TEXT")
    private String motivoEscalonamento;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
