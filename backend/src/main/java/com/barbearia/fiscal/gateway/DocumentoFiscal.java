package com.barbearia.fiscal.gateway;

/** Resultado da emissao: o conteudo do arquivo e um identificador externo (ex.: numero de protocolo de um provedor real, na Fase 16). */
public record DocumentoFiscal(byte[] conteudo, String identificadorExterno) {
}
