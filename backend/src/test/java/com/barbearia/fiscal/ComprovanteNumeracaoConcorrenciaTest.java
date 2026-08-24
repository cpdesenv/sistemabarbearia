package com.barbearia.fiscal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.OrigemAgendamento;
import com.barbearia.agenda.domain.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.financeiro.domain.Comanda;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.repository.ComandaRepository;
import com.barbearia.fiscal.service.ComprovanteService;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.shared.IntegrationTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova a garantia central da Fase 6: numeracao sequencial sem buraco nem
 * duplicidade sob concorrencia real. Cada tarefa roda em SUA PROPRIA
 * transacao (via {@link TransactionTemplate}, em threads separadas — nao
 * dentro da transacao do metodo de teste), exatamente como aconteceria com
 * varios fechamentos de comanda simultaneos vindos do painel.
 */
class ComprovanteNumeracaoConcorrenciaTest extends IntegrationTestBase {

    private static final int QUANTIDADE_CONCORRENTE = 20;

    @Autowired
    private ComprovanteService comprovanteService;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private ComandaRepository comandaRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void reservarNumeroConcorrentementeNaoGeraBuracoNemDuplicidade() throws Exception {
        Profissional profissional = profissionalRepository.save(novoProfissional());
        List<Long> comandaIds = IntStream.range(0, QUANTIDADE_CONCORRENTE)
                .mapToObj(indice -> criarComandaFechada(profissional, indice).getId())
                .toList();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(QUANTIDADE_CONCORRENTE);
        try {
            List<Callable<Long>> tarefas = comandaIds.stream()
                    .<Callable<Long>>map(comandaId -> () -> transactionTemplate.execute(status -> {
                        Comanda comanda = comandaRepository.findById(comandaId).orElseThrow();
                        return comprovanteService.reservarParaComanda(comanda).getNumero();
                    }))
                    .toList();

            List<Future<Long>> resultados = executor.invokeAll(tarefas);
            List<Long> numeros = new ArrayList<>();
            for (Future<Long> resultado : resultados) {
                numeros.add(resultado.get());
            }

            assertThat(numeros).hasSize(QUANTIDADE_CONCORRENTE);
            assertThat(numeros).doesNotHaveDuplicates();

            long minimo = numeros.stream().mapToLong(Long::longValue).min().orElseThrow();
            long maximo = numeros.stream().mapToLong(Long::longValue).max().orElseThrow();
            assertThat(maximo - minimo + 1)
                    .as("numeros reservados devem formar uma faixa continua, sem buraco")
                    .isEqualTo((long) QUANTIDADE_CONCORRENTE);
        } finally {
            executor.shutdown();
        }
    }

    private Profissional novoProfissional() {
        Profissional profissional = new Profissional();
        profissional.setNome("Profissional Concorrencia " + UUID.randomUUID());
        profissional.setCorAgenda("#3F51B5");
        profissional.setComissaoPercentualPadrao(BigDecimal.ZERO);
        return profissional;
    }

    private Comanda criarComandaFechada(Profissional profissional, int indiceOffset) {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente Concorrencia " + UUID.randomUUID());
        cliente = clienteRepository.save(cliente);

        Instant inicio = Instant.now().plus(indiceOffset, ChronoUnit.HOURS);
        Agendamento agendamento = new Agendamento();
        agendamento.setCliente(cliente);
        agendamento.setProfissional(profissional);
        agendamento.setInicio(inicio);
        agendamento.setFim(inicio.plus(30, ChronoUnit.MINUTES));
        agendamento.setValorTotal(BigDecimal.valueOf(50));
        agendamento.setStatus(StatusAgendamento.FINALIZADO);
        agendamento.setOrigem(OrigemAgendamento.PAINEL);
        agendamento = agendamentoRepository.save(agendamento);

        Comanda comanda = new Comanda();
        comanda.setAgendamento(agendamento);
        comanda.setStatus(StatusComanda.FECHADA);
        comanda.setFechadaEm(Instant.now());
        return comandaRepository.save(comanda);
    }
}
