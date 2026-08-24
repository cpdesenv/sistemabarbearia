package com.barbearia.fiscal.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.barbearia.financeiro.domain.Comanda;

/**
 * Comprovante em PDF de uma comanda fechada (recibo interno, sem valor
 * fiscal — a Fase 16 troca o {@code FiscalGateway} por um emissor de NFS-e
 * real, sem tocar nesta entidade nem no fluxo de comanda).
 *
 * <p>O {@link #numero} e reservado atomicamente com o fechamento da comanda
 * (ver {@code ComprovanteService#reservarParaComanda}, chamado de dentro de
 * {@code ComandaService#fechar}), garantindo que nunca haja buraco na
 * numeracao. A geracao do arquivo em si (PDF + upload para o storage) roda
 * depois, em uma transacao separada, para que uma falha de storage nunca
 * bloqueie o fechamento financeiro da comanda — ver {@link #status}.
 */
@Entity
@Table(name = "comprovante")
@Getter
@Setter
@NoArgsConstructor
public class Comprovante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comanda_id", nullable = false, unique = true)
    private Comanda comanda;

    @Column(nullable = false, unique = true)
    private Long numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusComprovante status = StatusComprovante.PENDENTE;

    @Column(name = "cliente_nome_snapshot", nullable = false)
    private String clienteNomeSnapshot;

    @Column(name = "cliente_telefone_snapshot")
    private String clienteTelefoneSnapshot;

    @Column(name = "cliente_email_snapshot")
    private String clienteEmailSnapshot;

    @Column(name = "chave_armazenamento")
    private String chaveArmazenamento;

    @Column(name = "gerado_em")
    private Instant geradoEm;

    @Column(name = "ultimo_erro")
    private String ultimoErro;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
