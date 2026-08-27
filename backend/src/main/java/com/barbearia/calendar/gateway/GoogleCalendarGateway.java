package com.barbearia.calendar.gateway;

import java.io.IOException;
import java.util.Date;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.AgendamentoServico;
import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.calendar.config.CalendarProperties;
import com.barbearia.calendar.service.IntegracaoGoogleCalendarService;

/**
 * Implementacao real via SDK oficial do Google. As credenciais sao
 * reconstruidas a cada chamada a partir do refresh token descriptografado —
 * a renovacao do access token e feita automaticamente pelo
 * {@link HttpCredentialsAdapter}/{@link UserCredentials}, sem nenhum codigo
 * manual de refresh aqui.
 */
@Component
@ConditionalOnProperty(prefix = "app.calendar", name = "gateway", havingValue = "google")
public class GoogleCalendarGateway implements CalendarGateway {

    private static final String NOME_APLICACAO = "Sistema Barbearia";

    private final IntegracaoGoogleCalendarService integracaoService;
    private final CalendarProperties propriedades;
    private final BarbeariaRepository barbeariaRepository;

    public GoogleCalendarGateway(IntegracaoGoogleCalendarService integracaoService, CalendarProperties propriedades,
            BarbeariaRepository barbeariaRepository) {
        this.integracaoService = integracaoService;
        this.propriedades = propriedades;
        this.barbeariaRepository = barbeariaRepository;
    }

    @Override
    public EventoCriadoResultado criarEvento(Agendamento agendamento) {
        String calendarioId = integracaoService.resolverCalendarId(agendamento.getProfissional());
        Event evento = montarEvento(agendamento);
        try {
            Event criado = clienteCalendar().events().insert(calendarioId, evento).execute();
            return new EventoCriadoResultado(criado.getId(), calendarioId);
        } catch (IOException e) {
            throw new CalendarSyncException("Falha ao criar evento no Google Calendar.", e);
        }
    }

    @Override
    public void atualizarEvento(Agendamento agendamento) {
        Event evento = montarEvento(agendamento);
        try {
            clienteCalendar().events()
                    .update(agendamento.getGoogleCalendarId(), agendamento.getGoogleEventId(), evento)
                    .execute();
        } catch (IOException e) {
            throw new CalendarSyncException("Falha ao atualizar evento no Google Calendar.", e);
        }
    }

    @Override
    public void removerEvento(Agendamento agendamento) {
        try {
            clienteCalendar().events()
                    .delete(agendamento.getGoogleCalendarId(), agendamento.getGoogleEventId())
                    .execute();
        } catch (IOException e) {
            throw new CalendarSyncException("Falha ao remover evento do Google Calendar.", e);
        }
    }

    private Calendar clienteCalendar() {
        String refreshToken = integracaoService.obterRefreshTokenDescriptografado();
        CalendarProperties.Google google = propriedades.getGoogle();

        UserCredentials credenciais = UserCredentials.newBuilder()
                .setClientId(google.getClientId())
                .setClientSecret(google.getClientSecret())
                .setRefreshToken(refreshToken)
                .build();

        try {
            return new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credenciais))
                    .setApplicationName(NOME_APLICACAO)
                    .build();
        } catch (Exception e) {
            throw new CalendarSyncException("Falha ao inicializar cliente do Google Calendar.", e);
        }
    }

    private Event montarEvento(Agendamento agendamento) {
        String fusoHorario = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .map(Barbearia::getFusoHorario)
                .orElse("America/Sao_Paulo");

        String nomesServicos = agendamento.getServicos().stream()
                .map(AgendamentoServico::getServico)
                .map(servico -> servico.getNome())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Servico");

        StringBuilder descricao = new StringBuilder("Telefone: ").append(agendamento.getCliente().getTelefone());
        if (agendamento.getObservacao() != null && !agendamento.getObservacao().isBlank()) {
            descricao.append("\nObservacao: ").append(agendamento.getObservacao());
        }

        Event evento = new Event()
                .setSummary(nomesServicos + " — " + agendamento.getCliente().getNome())
                .setDescription(descricao.toString())
                .setColorId(corDoEvento(agendamento.getProfissional().getId()));
        evento.setStart(new EventDateTime()
                .setDateTime(new DateTime(Date.from(agendamento.getInicio())))
                .setTimeZone(fusoHorario));
        evento.setEnd(new EventDateTime()
                .setDateTime(new DateTime(Date.from(agendamento.getFim())))
                .setTimeZone(fusoHorario));
        return evento;
    }

    /**
     * O Google Calendar so aceita um id de cor entre 1 e 11 (paleta fixa da
     * API, sem suporte a hex arbitrario) — por isso o profissional nao usa
     * diretamente {@code corAgenda} aqui. No modo CALENDARIO_UNICO, isso e o
     * que diferencia visualmente os profissionais no mesmo calendario ("com
     * cores", ver PRD); no modo POR_PROFISSIONAL a cor e so um extra, ja que
     * cada um tem seu proprio calendario.
     */
    private String corDoEvento(Long profissionalId) {
        long indice = Math.floorMod(profissionalId, 11) + 1;
        return String.valueOf(indice);
    }
}
