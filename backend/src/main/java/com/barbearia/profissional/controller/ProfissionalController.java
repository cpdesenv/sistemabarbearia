package com.barbearia.profissional.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.horario.dto.JanelaHorarioDto;
import com.barbearia.horario.dto.SalvarJanelaHorarioRequest;
import com.barbearia.horario.service.GradeHorariaService;
import com.barbearia.profissional.dto.AssociarServicoRequest;
import com.barbearia.profissional.dto.AtualizarStatusProfissionalRequest;
import com.barbearia.profissional.dto.ProfissionalDto;
import com.barbearia.profissional.dto.SalvarProfissionalRequest;
import com.barbearia.profissional.dto.ServicoVinculadoDto;
import com.barbearia.profissional.service.ProfissionalService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
@Tag(name = "Profissionais")
public class ProfissionalController {

    private final ProfissionalService profissionalService;
    private final GradeHorariaService gradeHorariaService;

    @GetMapping
    public PagedModel<ProfissionalDto> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        Page<ProfissionalDto> pagina = profissionalService.listar(nome, ativo, pageable);
        return new PagedModel<>(pagina);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ProfissionalDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(profissionalService.obter(uuid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProfissionalDto criar(@Valid @RequestBody SalvarProfissionalRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return profissionalService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ProfissionalDto> atualizar(@PathVariable UUID uuid,
            @Valid @RequestBody SalvarProfissionalRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(profissionalService.atualizar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @PatchMapping("/{uuid}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ProfissionalDto> atualizarStatus(@PathVariable UUID uuid,
            @Valid @RequestBody AtualizarStatusProfissionalRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(profissionalService.atualizarStatus(uuid, requisicao,
                principal.getUsuario().getId(), httpRequest));
    }

    @GetMapping("/{uuid}/servicos")
    public List<ServicoVinculadoDto> listarServicos(@PathVariable UUID uuid) {
        return profissionalService.listarServicosVinculados(uuid);
    }

    @PutMapping("/{uuid}/servicos")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<ServicoVinculadoDto> sincronizarServicos(@PathVariable UUID uuid,
            @Valid @RequestBody List<AssociarServicoRequest> requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return profissionalService.sincronizarServicos(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest);
    }

    @GetMapping("/{uuid}/grade-horaria")
    public List<JanelaHorarioDto> obterGradeHoraria(@PathVariable UUID uuid) {
        return gradeHorariaService.listar(uuid);
    }

    @PutMapping("/{uuid}/grade-horaria")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<JanelaHorarioDto> sincronizarGradeHoraria(@PathVariable UUID uuid,
            @Valid @RequestBody List<SalvarJanelaHorarioRequest> requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return gradeHorariaService.sincronizar(uuid, requisicao, principal.getUsuario().getId(), httpRequest);
    }
}
