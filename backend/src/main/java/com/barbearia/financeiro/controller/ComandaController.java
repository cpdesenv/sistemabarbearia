package com.barbearia.financeiro.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.financeiro.dto.AdicionarItemComandaRequest;
import com.barbearia.financeiro.dto.AdicionarItemProdutoComandaRequest;
import com.barbearia.financeiro.dto.AplicarDescontoRequest;
import com.barbearia.financeiro.dto.ComandaDto;
import com.barbearia.financeiro.dto.DefinirFormaPagamentoRequest;
import com.barbearia.financeiro.dto.EstornarComandaRequest;
import com.barbearia.financeiro.service.ComandaService;
import com.barbearia.fiscal.service.ComprovanteService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/comandas")
@RequiredArgsConstructor
@Tag(name = "Comandas")
public class ComandaController {

    private final ComandaService comandaService;
    private final ComprovanteService comprovanteService;

    @PostMapping("/abrir-para-agendamento/{agendamentoUuid}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ComandaDto abrirParaAgendamento(@PathVariable UUID agendamentoUuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return comandaService.abrirParaAgendamento(agendamentoUuid, principal.getUsuario().getId(), httpRequest);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ComandaDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(comandaService.obter(uuid));
    }

    @GetMapping("/por-agendamento/{agendamentoUuid}")
    public ResponseEntity<ComandaDto> obterPorAgendamento(@PathVariable UUID agendamentoUuid) {
        return ResponseEntity.ok(comandaService.obterMaisRecentePorAgendamento(agendamentoUuid));
    }

    @PostMapping("/{uuid}/itens")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<ComandaDto> adicionarItem(@PathVariable UUID uuid,
            @Valid @RequestBody AdicionarItemComandaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(comandaService.adicionarItemServico(uuid, requisicao,
                principal.getUsuario().getId(), httpRequest));
    }

    @PostMapping("/{uuid}/itens/produto")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<ComandaDto> adicionarItemProduto(@PathVariable UUID uuid,
            @Valid @RequestBody AdicionarItemProdutoComandaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(comandaService.adicionarItemProduto(uuid, requisicao,
                principal.getUsuario().getId(), httpRequest));
    }

    @DeleteMapping("/{uuid}/itens/{itemUuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<ComandaDto> removerItem(@PathVariable UUID uuid, @PathVariable UUID itemUuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(comandaService.removerItem(uuid, itemUuid, principal.getUsuario().getId(),
                httpRequest));
    }

    @PutMapping("/{uuid}/desconto")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<ComandaDto> aplicarDesconto(@PathVariable UUID uuid,
            @Valid @RequestBody AplicarDescontoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(comandaService.aplicarDesconto(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @PutMapping("/{uuid}/forma-pagamento")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<ComandaDto> definirFormaPagamento(@PathVariable UUID uuid,
            @Valid @RequestBody DefinirFormaPagamentoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(comandaService.definirFormaPagamento(uuid, requisicao,
                principal.getUsuario().getId(), httpRequest));
    }

    @PostMapping("/{uuid}/fechar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<ComandaDto> fechar(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        ComandaDto comanda = comandaService.fechar(uuid, principal.getUsuario().getId(), httpRequest);
        // So chamado apos o fechamento ja ter sido commitado (fora da transacao de
        // ComandaService#fechar) — o numero do comprovante ja foi reservado la
        // dentro; aqui so gera o arquivo, de forma resiliente (nunca lanca, ver
        // ComprovanteService#gerarArquivoParaComanda) para nao derrubar a resposta
        // de fechamento por causa de uma falha de storage.
        comprovanteService.gerarArquivoParaComanda(uuid);
        return ResponseEntity.ok(comanda);
    }

    @PostMapping("/{uuid}/estornar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ComandaDto> estornar(@PathVariable UUID uuid,
            @Valid @RequestBody EstornarComandaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(comandaService.estornar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }
}
