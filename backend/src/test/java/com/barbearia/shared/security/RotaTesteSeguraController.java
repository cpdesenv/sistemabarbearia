package com.barbearia.shared.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller que so existe no classpath de teste (src/test/java), usado para
 * provar de ponta a ponta que a cadeia de seguranca (401 sem token, 403 com
 * perfil insuficiente) funciona, sem precisar de um endpoint de negocio real
 * so' para isso — as regras de autorizacao de cada modulo aparecem a partir
 * da Fase 2.
 */
@RestController
public class RotaTesteSeguraController {

    @GetMapping("/api/teste/autenticado")
    public String autenticado() {
        return "ok";
    }

    @GetMapping("/api/teste/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String somenteAdmin() {
        return "ok-admin";
    }
}
