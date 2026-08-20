package com.barbearia.cliente.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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

import com.barbearia.cliente.domain.Cliente;
import com.barbearia.cliente.dto.AnonimizarClienteRequest;
import com.barbearia.cliente.dto.ClienteDto;
import com.barbearia.cliente.dto.ClienteDuplicadoResposta;
import com.barbearia.cliente.dto.ClienteResumoDto;
import com.barbearia.cliente.dto.ExportacaoClienteDto;
import com.barbearia.cliente.dto.FichaClienteDto;
import com.barbearia.cliente.dto.SalvarClienteRequest;
import com.barbearia.cliente.exception.ClienteDuplicadoException;
import com.barbearia.cliente.service.ClienteService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    public PagedModel<ClienteDto> listar(@RequestParam(required = false) String busca, Pageable pageable) {
        Page<ClienteDto> pagina = clienteService.listar(busca, pageable);
        return new PagedModel<>(pagina);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ClienteDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(clienteService.obter(uuid));
    }

    @GetMapping("/{uuid}/ficha")
    public ResponseEntity<FichaClienteDto> ficha(@PathVariable UUID uuid) {
        return ResponseEntity.ok(clienteService.ficha(uuid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO')")
    public ClienteDto criar(@Valid @RequestBody SalvarClienteRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return clienteService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO')")
    public ResponseEntity<ClienteDto> atualizar(@PathVariable UUID uuid,
            @Valid @RequestBody SalvarClienteRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(clienteService.atualizar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @GetMapping("/{uuid}/exportar-dados")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ExportacaoClienteDto> exportarDados(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(clienteService.exportarDados(uuid, principal.getUsuario().getId(), httpRequest));
    }

    @PostMapping("/{uuid}/anonimizar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ClienteDto> anonimizar(@PathVariable UUID uuid,
            @Valid @RequestBody AnonimizarClienteRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(clienteService.anonimizar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @ExceptionHandler(ClienteDuplicadoException.class)
    public ResponseEntity<ClienteDuplicadoResposta> tratarDuplicidade(ClienteDuplicadoException ex) {
        Cliente existente = ex.getClienteExistente();
        ClienteResumoDto resumo = new ClienteResumoDto(existente.getUuidPublico(), existente.getNome(),
                existente.getTelefone());
        ClienteDuplicadoResposta corpo = new ClienteDuplicadoResposta(Instant.now(), HttpStatus.CONFLICT.value(),
                "CLIENTE_DUPLICADO", ex.getMessage(), resumo);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo);
    }
}
