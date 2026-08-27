package com.barbearia.calendar.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.calendar.domain.AgendamentoCalendarOutbox;
import com.barbearia.calendar.domain.IntegracaoGoogleCalendar;
import com.barbearia.calendar.domain.ModoCalendario;
import com.barbearia.calendar.domain.StatusOutbox;
import com.barbearia.calendar.dto.AgendamentoForaDeSincroniaDto;
import com.barbearia.calendar.dto.AtualizarModoCalendarioRequest;
import com.barbearia.calendar.dto.DefinirCalendarioProfissionalRequest;
import com.barbearia.calendar.dto.StatusIntegracaoDto;
import com.barbearia.calendar.gateway.GoogleOAuthGateway;
import com.barbearia.calendar.repository.AgendamentoCalendarOutboxRepository;
import com.barbearia.calendar.repository.IntegracaoGoogleCalendarRepository;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.criptografia.CriptografiaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Dona do registro singleton {@link IntegracaoGoogleCalendar}: fluxo de
 * conexao/desconexao OAuth2, resolucao do calendario a usar para um
 * profissional, e o botao global de ressincronizacao. As chamadas reais ao
 * Google (criar/atualizar/remover evento) ficam em {@code CalendarGateway} —
 * esta classe so cuida da credencial e da configuracao.
 */
@Service
@RequiredArgsConstructor
public class IntegracaoGoogleCalendarService {

    private static final int MINUTOS_EXPIRACAO_STATE = 5;

    private final IntegracaoGoogleCalendarRepository integracaoRepository;
    private final AgendamentoCalendarOutboxRepository outboxRepository;
    private final ProfissionalRepository profissionalRepository;
    private final CriptografiaService criptografiaService;
    private final GoogleOAuthGateway oAuthGateway;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public StatusIntegracaoDto status() {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        return new StatusIntegracaoDto(integracao.conectado(), integracao.getModo(),
                integracao.getCalendarioIdUnico(), integracao.getConectadoEm(), integracao.getUltimoErro());
    }

    @Transactional
    public String iniciarConexao(Long usuarioId, HttpServletRequest httpRequest) {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        String state = UUID.randomUUID().toString();
        integracao.setStatePendente(state);
        integracao.setStateExpiraEm(Instant.now().plus(MINUTOS_EXPIRACAO_STATE, ChronoUnit.MINUTES));
        integracao.setStateIniciadoPorUsuarioId(usuarioId);
        integracaoRepository.save(integracao);

        auditoriaService.registrar(usuarioId, "GOOGLE_CALENDAR_CONEXAO_INICIADA", "integracao_google_calendar",
                IntegracaoGoogleCalendar.ID_SINGLETON, "Conexao com o Google Calendar iniciada", httpRequest);

        return oAuthGateway.gerarUrlAutorizacao(state);
    }

    /** Chamado pelo navegador de volta do Google (rota publica) — autorizacao e feita pelo `state`, nao por JWT. */
    @Transactional
    public void processarCallback(String codigo, String state) {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        boolean stateValido = integracao.getStatePendente() != null
                && integracao.getStatePendente().equals(state)
                && integracao.getStateExpiraEm() != null
                && integracao.getStateExpiraEm().isAfter(Instant.now());
        if (!stateValido) {
            throw new NegocioException("Solicitacao de conexao invalida ou expirada. Tente conectar novamente.");
        }

        Long usuarioIniciador = integracao.getStateIniciadoPorUsuarioId();
        String refreshToken = oAuthGateway.trocarCodigoPorRefreshToken(codigo);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new NegocioException(
                    "O Google nao retornou um refresh token. Revogue o acesso do app na sua conta Google e tente conectar de novo.");
        }

        integracao.setRefreshTokenCriptografado(criptografiaService.criptografar(refreshToken));
        integracao.setConectadoEm(Instant.now());
        integracao.setConectadoPorUsuarioId(usuarioIniciador);
        integracao.setUltimoErro(null);
        integracao.setStatePendente(null);
        integracao.setStateExpiraEm(null);
        integracao.setStateIniciadoPorUsuarioId(null);
        integracaoRepository.save(integracao);

        auditoriaService.registrar(usuarioIniciador, "GOOGLE_CALENDAR_CONECTADO", "integracao_google_calendar",
                IntegracaoGoogleCalendar.ID_SINGLETON, "Google Calendar conectado com sucesso", null);
    }

    @Transactional
    public void desconectar(Long usuarioId, HttpServletRequest httpRequest) {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        integracao.setRefreshTokenCriptografado(null);
        integracao.setConectadoEm(null);
        integracao.setConectadoPorUsuarioId(null);
        integracao.setUltimoErro(null);
        integracaoRepository.save(integracao);

        auditoriaService.registrar(usuarioId, "GOOGLE_CALENDAR_DESCONECTADO", "integracao_google_calendar",
                IntegracaoGoogleCalendar.ID_SINGLETON, "Google Calendar desconectado", httpRequest);
    }

    @Transactional
    public AtualizarModoCalendarioRequest atualizarModo(AtualizarModoCalendarioRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        integracao.setModo(requisicao.modo());
        integracao.setCalendarioIdUnico(requisicao.calendarioIdUnico());
        integracaoRepository.save(integracao);

        auditoriaService.registrar(usuarioId, "GOOGLE_CALENDAR_MODO_ATUALIZADO", "integracao_google_calendar",
                IntegracaoGoogleCalendar.ID_SINGLETON, "Modo de calendario alterado para " + requisicao.modo(),
                httpRequest);

        return requisicao;
    }

    @Transactional
    public void definirCalendarioProfissional(UUID profissionalUuid, DefinirCalendarioProfissionalRequest requisicao,
            Long usuarioId, HttpServletRequest httpRequest) {
        Profissional profissional = profissionalRepository.findByUuidPublico(profissionalUuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional nao encontrado."));
        profissional.setGoogleCalendarId(requisicao.googleCalendarId());
        profissionalRepository.save(profissional);

        auditoriaService.registrar(usuarioId, "GOOGLE_CALENDAR_PROFISSIONAL_DEFINIDO", "profissional",
                profissional.getId(), "Google Calendar do profissional '" + profissional.getNome() + "' atualizado",
                httpRequest);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoForaDeSincroniaDto> listarForaDeSincronia() {
        return outboxRepository
                .findByStatusOrTentativasGreaterThanOrderByProximaTentativaEmAsc(StatusOutbox.FALHA_PERMANENTE, 0)
                .stream()
                .map(linha -> new AgendamentoForaDeSincroniaDto(
                        linha.getAgendamento().getUuidPublico(),
                        linha.getAgendamento().getCliente().getNome(),
                        linha.getAgendamento().getInicio(),
                        linha.getTipoOperacao(),
                        linha.getStatus(),
                        linha.getTentativas(),
                        linha.getUltimoErro()))
                .toList();
    }

    @Transactional
    public int ressincronizar(Long usuarioId, HttpServletRequest httpRequest) {
        List<AgendamentoCalendarOutbox> pendencias = outboxRepository.findByStatusNot(StatusOutbox.CONCLUIDO);
        Instant agora = Instant.now();
        for (AgendamentoCalendarOutbox linha : pendencias) {
            linha.setStatus(StatusOutbox.PENDENTE);
            linha.setTentativas(0);
            linha.setProximaTentativaEm(agora);
            linha.setUltimoErro(null);
        }
        outboxRepository.saveAll(pendencias);

        auditoriaService.registrar(usuarioId, "GOOGLE_CALENDAR_RESSINCRONIZADO", "integracao_google_calendar",
                IntegracaoGoogleCalendar.ID_SINGLETON,
                "Ressincronizacao manual solicitada (" + pendencias.size() + " agendamento(s))", httpRequest);

        return pendencias.size();
    }

    /** Usado pelo {@code GoogleCalendarGateway} para saber em qual calendario criar o evento. */
    @Transactional(readOnly = true)
    public String resolverCalendarId(Profissional profissional) {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        if (integracao.getModo() == ModoCalendario.POR_PROFISSIONAL) {
            String calendarioProfissional = profissional.getGoogleCalendarId();
            if (calendarioProfissional == null || calendarioProfissional.isBlank()) {
                throw new NegocioException(
                        "Profissional '" + profissional.getNome() + "' nao tem Google Calendar configurado.");
            }
            return calendarioProfissional;
        }
        if (integracao.getCalendarioIdUnico() == null || integracao.getCalendarioIdUnico().isBlank()) {
            throw new NegocioException("Nenhum Google Calendar configurado para a barbearia.");
        }
        return integracao.getCalendarioIdUnico();
    }

    /** Usado pelo {@code GoogleCalendarGateway} para montar as credenciais da chamada. Nunca logar o valor retornado. */
    @Transactional(readOnly = true)
    public String obterRefreshTokenDescriptografado() {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        if (!integracao.conectado()) {
            throw new NegocioException("Google Calendar nao esta conectado.");
        }
        return criptografiaService.descriptografar(integracao.getRefreshTokenCriptografado());
    }

    @Transactional
    public void registrarUltimoErro(String mensagem) {
        IntegracaoGoogleCalendar integracao = buscarSingleton();
        integracao.setUltimoErro(mensagem);
        integracaoRepository.save(integracao);
    }

    private IntegracaoGoogleCalendar buscarSingleton() {
        return integracaoRepository.findById(IntegracaoGoogleCalendar.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao de integracao com o Google Calendar nao encontrada. Verifique se as migrations foram executadas."));
    }
}
