package com.barbearia.fiscal.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

/**
 * Implementacao real do {@link ObjectStorageService}, sobre o MinIO ja
 * provisionado no docker-compose desde a Fase 0. O bucket e criado sob
 * demanda (idempotente) na primeira gravacao — nao ha migration de
 * infraestrutura de storage, so de banco.
 */
@Component
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketVerificado = false;

    public MinioObjectStorageService(
            @Value("${app.storage.minio.endpoint}") String endpoint,
            @Value("${app.storage.minio.access-key}") String accessKey,
            @Value("${app.storage.minio.secret-key}") String secretKey,
            @Value("${app.storage.minio.bucket}") String bucket) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    @Override
    public String salvar(String chave, byte[] conteudo, String contentType) {
        try {
            garantirBucket();
            try (ByteArrayInputStream entrada = new ByteArrayInputStream(conteudo)) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(chave)
                        .stream(entrada, conteudo.length, -1)
                        .contentType(contentType)
                        .build());
            }
            return chave;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar objeto '" + chave + "' no storage.", e);
        }
    }

    @Override
    public byte[] carregar(String chave) {
        try (var objeto = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(chave)
                .build())) {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            objeto.transferTo(saida);
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar objeto '" + chave + "' do storage.", e);
        }
    }

    private void garantirBucket() throws Exception {
        if (bucketVerificado) {
            return;
        }
        synchronized (this) {
            if (bucketVerificado) {
                return;
            }
            boolean existe = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!existe) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketVerificado = true;
        }
    }
}
