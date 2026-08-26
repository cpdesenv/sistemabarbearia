package com.barbearia.assinatura.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.assinatura.domain.StatusAssinatura;
import com.barbearia.assinatura.dto.AssinaturaDto;
import com.barbearia.assinatura.dto.AssinaturaResumoDto;
import com.barbearia.assinatura.dto.CancelarAssinaturaRequest;
import com.barbearia.assinatura.dto.CriarAssinaturaRequest;
import com.barbearia.assinatura.dto.RelatorioReceitaAssinaturaDto;
import com.barbearia.assinatura.service.AssinaturaService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/assinaturas")
@RequiredArgsConstructor
@Tag(name = "Assinaturas (Clube Cavalinho)")
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    @GetMapping
    public List<AssinaturaDto> listar(@RequestParam(required = false) StatusAssinatura status) {
        return assinaturaService.listar(status);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<AssinaturaDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(assinaturaService.obter(uuid));
    }

    @GetMapping("/resumo")
    public AssinaturaResumoDto resumo() {
        return assinaturaService.resumo();
    }

    @GetMapping("/relatorio-receita")
    public RelatorioReceitaAssinaturaDto relatorioReceita(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return assinaturaService.relatorioReceita(mes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO')")
    public AssinaturaDto assinar(@Valid @RequestBody CriarAssinaturaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return assinaturaService.assinar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PostMapping("/{uuid}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<AssinaturaDto> cancelar(@PathVariable UUID uuid,
            @Valid @RequestBody CancelarAssinaturaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(assinaturaService.cancelar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }
}
