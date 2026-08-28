package com.barbearia.fiscal.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(LogEmailGateway.class);

    @Override
    public void enviarComprovante(String destinatario, String clienteNome, long numeroComprovante,
            byte[] anexoPdf) {
        log.info("[MOCK EMAIL] Comprovante #{} enviado para {} ({}) — {} bytes anexados.",
                numeroComprovante, destinatario, clienteNome, anexoPdf.length);
    }

    @Override
    public void enviarConfirmacaoAgendamento(String destinatario, String clienteNome, String resumoAgendamento) {
        log.info("[MOCK EMAIL] Confirmacao de agendamento enviada para {} ({}): {}", destinatario, clienteNome,
                resumoAgendamento);
    }
}
