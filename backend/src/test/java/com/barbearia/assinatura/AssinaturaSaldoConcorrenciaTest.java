package com.barbearia.assinatura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

import com.barbearia.assinatura.domain.Assinatura;
import com.barbearia.assinatura.domain.PlanoAssinatura;
import com.barbearia.assinatura.domain.StatusAssinatura;
import com.barbearia.assinatura.repository.AssinaturaRepository;
import com.barbearia.assinatura.repository.PlanoAssinaturaRepository;
import com.barbearia.assinatura.service.AssinaturaService;
import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.repository.ClienteRepository;
import com.barbearia.servico.domain.Servico;
import com.barbearia.servico.repository.ServicoRepository;
import com.barbearia.shared.IntegrationTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova o criterio de aceite "dois agendamentos simultaneos nao consomem dois
 * saldos": com saldo = 1, N tentativas concorrentes de consumo (cada uma na
 * SUA PROPRIA transacao, em threads separadas — nao dentro da transacao do
 * metodo de teste) devem resultar em exatamente 1 sucesso, no mesmo padrao de
 * {@code ComprovanteNumeracaoConcorrenciaTest}.
 */
class AssinaturaSaldoConcorrenciaTest extends IntegrationTestBase {

    private static final int QUANTIDADE_CONCORRENTE = 20;

    @Autowired
    private AssinaturaService assinaturaService;
    @Autowired
    private AssinaturaRepository assinaturaRepository;
    @Autowired
    private PlanoAssinaturaRepository planoAssinaturaRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void consumoConcorrenteComSaldoUmSoPermiteUmSucesso() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long assinaturaId = transactionTemplate.execute(status -> {
            Servico servico = new Servico();
            servico.setNome("Corte Concorrencia " + UUID.randomUUID());
            servico.setPreco(BigDecimal.valueOf(50));
            servico.setDuracaoMinutos(30);
            servico = servicoRepository.save(servico);

            PlanoAssinatura plano = new PlanoAssinatura();
            plano.setNome("Plano Concorrencia " + UUID.randomUUID());
            plano.setPrecoMensal(BigDecimal.valueOf(80));
            plano.setCortesIncluidosPorCiclo(1);
            plano.setPercentualDescontoAdicional(BigDecimal.ZERO);
            Set<Servico> servicosInclusos = new HashSet<>();
            servicosInclusos.add(servico);
            plano.setServicosInclusos(servicosInclusos);
            plano = planoAssinaturaRepository.save(plano);

            Cliente cliente = new Cliente();
            cliente.setNome("Cliente Concorrencia " + UUID.randomUUID());
            cliente = clienteRepository.save(cliente);

            Assinatura assinatura = new Assinatura();
            assinatura.setCliente(cliente);
            assinatura.setPlano(plano);
            assinatura.setStatus(StatusAssinatura.ATIVA);
            assinatura.setSaldoCortesAtual(1);
            assinatura.setDataInicio(LocalDate.now());
            assinatura.setDataProximaRenovacao(LocalDate.now().plusMonths(1));
            assinatura = assinaturaRepository.save(assinatura);

            return assinatura.getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(QUANTIDADE_CONCORRENTE);
        try {
            List<Callable<Boolean>> tarefas = IntStream.range(0, QUANTIDADE_CONCORRENTE)
                    .<Callable<Boolean>>mapToObj(indice -> () -> transactionTemplate.execute(status -> {
                        Assinatura assinatura = assinaturaRepository.findById(assinaturaId).orElseThrow();
                        Cliente cliente = assinatura.getCliente();
                        Servico servico = assinatura.getPlano().getServicosInclusos().iterator().next();
                        Optional<Assinatura> resultado = assinaturaService.tentarConsumirSaldo(cliente, servico);
                        return resultado.isPresent();
                    }))
                    .toList();

            List<Future<Boolean>> resultados = executor.invokeAll(tarefas);
            List<Boolean> sucessos = new ArrayList<>();
            for (Future<Boolean> resultado : resultados) {
                sucessos.add(resultado.get());
            }

            long totalSucessos = sucessos.stream().filter(Boolean::booleanValue).count();
            assertThat(totalSucessos)
                    .as("com saldo = 1, exatamente uma das tentativas concorrentes deve conseguir consumir")
                    .isEqualTo(1);

            Assinatura assinaturaFinal = assinaturaRepository.findById(assinaturaId).orElseThrow();
            assertThat(assinaturaFinal.getSaldoCortesAtual()).isZero();
        } finally {
            executor.shutdown();
        }
    }
}
