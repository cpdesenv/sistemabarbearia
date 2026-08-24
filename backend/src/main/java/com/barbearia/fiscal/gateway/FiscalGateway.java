package com.barbearia.fiscal.gateway;

/**
 * Abstrai a emissao do documento entregue ao cliente apos o fechamento da
 * comanda. Implementacao atual ({@link ReciboFiscalGateway}) gera um recibo
 * interno em PDF, sem nenhum valor fiscal. A Fase 16 troca essa
 * implementacao por um emissor de NFS-e real (via provedor homologado) —
 * nenhum outro ponto do sistema (fluxo de comanda, controller, frontend)
 * precisa mudar quando isso acontecer.
 */
public interface FiscalGateway {

    DocumentoFiscal emitirNotaFiscal(DadosComprovante dados);

    DocumentoFiscal consultarNotaFiscal(String identificadorExterno);

    void cancelarNotaFiscal(String identificadorExterno, String motivo);
}
