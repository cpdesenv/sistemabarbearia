package com.barbearia.fiscal.storage;

/** Abstrai o storage de objetos usado para guardar os PDFs de comprovante. */
public interface ObjectStorageService {

    /** Salva o conteudo sob a chave informada e retorna a mesma chave (para uso posterior em {@link #carregar}). */
    String salvar(String chave, byte[] conteudo, String contentType);

    byte[] carregar(String chave);
}
