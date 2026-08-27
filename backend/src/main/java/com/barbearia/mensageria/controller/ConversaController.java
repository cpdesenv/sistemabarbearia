package com.barbearia.mensageria.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.barbearia.mensageria.dto.ConversaDto;
import com.barbearia.mensageria.dto.MensagemDto;
import com.barbearia.mensageria.service.ConversaService;

@RestController
@RequestMapping("/api/conversas")
@RequiredArgsConstructor
@Tag(name = "Conversas (WhatsApp)")
public class ConversaController {

    private final ConversaService conversaService;

    @GetMapping
    public PagedModel<ConversaDto> listar(Pageable pageable) {
        Page<ConversaDto> pagina = conversaService.listar(pageable);
        return new PagedModel<>(pagina);
    }

    @GetMapping("/{uuid}/mensagens")
    public ResponseEntity<List<MensagemDto>> listarMensagens(@PathVariable UUID uuid) {
        return ResponseEntity.ok(conversaService.listarMensagens(uuid));
    }
}
