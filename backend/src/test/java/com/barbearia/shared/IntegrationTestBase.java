package com.barbearia.shared;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para testes de integracao com banco real (Postgres via Testcontainers)
 * e MockMvc, reaproveitada por todas as fases que precisarem do mesmo tipo de
 * teste (login, CRUDs, agenda etc.).
 *
 * O container e' iniciado manualmente num bloco estatico ("singleton
 * container pattern"), em vez de usar @Container/@Testcontainers: essa
 * extensao chama stop() no afterAll de CADA classe de teste, mesmo em campos
 * estaticos herdados, o que derrubava o container no meio da suite (a
 * segunda classe de teste herdava do Spring um DataSource em cache apontando
 * pra um container ja parado). O Ryuk ainda encerra o container no fim da
 * JVM.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    static {
        postgres.start();
    }
}
