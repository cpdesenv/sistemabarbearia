package com.barbearia.cliente.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @Column(nullable = false)
    private String nome;

    private String telefone;

    private String whatsapp;

    private String cpf;

    private String email;

    private String logradouro;

    private String numero;

    private String complemento;

    private String bairro;

    private String cidade;

    private String uf;

    private String cep;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    private String observacoes;

    @Column(name = "opt_in_whatsapp", nullable = false)
    private boolean optInWhatsapp = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_cadastro", nullable = false)
    private OrigemCadastro origemCadastro = OrigemCadastro.PAINEL;

    @Column(name = "consentimento_lgpd", nullable = false)
    private boolean consentimentoLgpd = false;

    @Column(name = "consentimento_lgpd_em")
    private Instant consentimentoLgpdEm;

    @Column(name = "anonimizado_em")
    private Instant anonimizadoEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public boolean isAnonimizado() {
        return anonimizadoEm != null;
    }
}
