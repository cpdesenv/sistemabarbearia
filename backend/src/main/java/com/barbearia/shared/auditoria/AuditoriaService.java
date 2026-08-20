package com.barbearia.shared.auditoria;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.shared.web.ClientIpResolver;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    public void registrar(Long usuarioId, String operacao, String entidade, Long entidadeId, String descricao,
            HttpServletRequest request) {
        String ip = request != null ? ClientIpResolver.resolver(request) : null;
        auditoriaRepository.save(new Auditoria(usuarioId, operacao, entidade, entidadeId, descricao, ip));
    }
}
