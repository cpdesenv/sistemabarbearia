package com.barbearia.fiscal.email;

/**
 * Abstrai o envio de e-mail. Nao ha nenhum provedor de e-mail configurado
 * no projeto ainda — a implementacao atual ({@link LogEmailGateway}) apenas
 * registra a tentativa, seguindo a mesma convencao de integracao mockada
 * usada para o Google Calendar (Fase 8): a interface ja fica pronta para uma
 * implementacao real (SMTP/provedor) entrar depois, sem tocar em quem a
 * chama.
 */
public interface EmailGateway {

    void enviarComprovante(String destinatario, String clienteNome, long numeroComprovante, byte[] anexoPdf);

    /** Confirmação de agendamento feito pelo link público de autoagendamento (Fase 9). */
    void enviarConfirmacaoAgendamento(String destinatario, String clienteNome, String resumoAgendamento);
}
