package com.barbearia.fiscal.email;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Abstrai o envio de e-mail. Nao ha nenhum provedor de e-mail configurado
 * no projeto ainda — a implementacao atual ({@link LogEmailGateway}) apenas
 * registra a tentativa: a interface ja fica pronta para uma implementacao
 * real (SMTP/provedor) entrar depois, sem tocar em quem a chama.
 */
public interface EmailGateway {

    void enviarComprovante(String destinatario, String clienteNome, long numeroComprovante, byte[] anexoPdf);

    void enviarConfirmacaoAgendamento(String destinatario, String clienteNome, String resumoServicos,
            String profissionalNome, Instant inicio, BigDecimal valorTotal);
}
