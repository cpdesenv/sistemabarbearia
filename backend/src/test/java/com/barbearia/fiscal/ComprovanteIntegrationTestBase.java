package com.barbearia.fiscal;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;

import com.barbearia.shared.IntegrationTestBase;

/**
 * Base para testes que precisam de um MinIO real (nao so do Postgres). Segue
 * o mesmo "singleton container pattern" documentado em
 * {@link IntegrationTestBase}: container estatico, iniciado uma vez,
 * encerrado pelo Ryuk no fim da JVM.
 */
public abstract class ComprovanteIntegrationTestBase extends IntegrationTestBase {

    static final MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

    static {
        minio.start();
    }

    @DynamicPropertySource
    static void configurarMinio(DynamicPropertyRegistry registry) {
        registry.add("app.storage.minio.endpoint", minio::getS3URL);
        registry.add("app.storage.minio.access-key", minio::getUserName);
        registry.add("app.storage.minio.secret-key", minio::getPassword);
        registry.add("app.storage.minio.bucket", () -> "comprovantes-teste");
    }
}
