package com.barbearia.calendar.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro unico de integracao com o Google Calendar (singleton, id fixo em
 * {@link #ID_SINGLETON}). {@link #refreshTokenCriptografado} e o unico
 * segredo persistido — o access token nunca e salvo (ver
 * {@code GoogleCalendarGateway}). {@link #statePendente}/{@link #stateExpiraEm}
 * guardam o token CSRF de uma autorizacao OAuth2 em andamento, de uso unico
 * e curta expiracao.
 */
@Entity
@Table(name = "integracao_google_calendar")
@Getter
@Setter
@NoArgsConstructor
public class IntegracaoGoogleCalendar {

    public static final long ID_SINGLETON = 1L;

    @Id
    private Long id = ID_SINGLETON;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModoCalendario modo = ModoCalendario.CALENDARIO_UNICO;

    @Column(name = "calendario_id_unico")
    private String calendarioIdUnico;

    @Column(name = "refresh_token_criptografado")
    private String refreshTokenCriptografado;

    @Column(name = "conectado_em")
    private Instant conectadoEm;

    @Column(name = "conectado_por_usuario_id")
    private Long conectadoPorUsuarioId;

    @Column(name = "state_pendente")
    private String statePendente;

    @Column(name = "state_expira_em")
    private Instant stateExpiraEm;

    @Column(name = "state_iniciado_por_usuario_id")
    private Long stateIniciadoPorUsuarioId;

    @Column(name = "ultimo_erro")
    private String ultimoErro;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public boolean conectado() {
        return refreshTokenCriptografado != null;
    }
}
