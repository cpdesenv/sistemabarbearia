package com.barbearia.fiscal.controller;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.barbearia.fiscal.dto.ComprovanteDto;
import com.barbearia.fiscal.service.ComprovanteService;

@RestController
@RequestMapping("/api/comandas/{comandaUuid}/comprovante")
@RequiredArgsConstructor
@Tag(name = "Comprovantes")
public class ComprovanteController {

    private final ComprovanteService comprovanteService;

    @GetMapping
    public ResponseEntity<ComprovanteDto> obterStatus(@PathVariable UUID comandaUuid) {
        return ResponseEntity.ok(comprovanteService.obterStatus(comandaUuid));
    }

    @GetMapping("/arquivo")
    public ResponseEntity<byte[]> baixar(@PathVariable UUID comandaUuid) {
        byte[] pdf = comprovanteService.baixar(comandaUuid);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("comprovante.pdf").build());
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @PostMapping("/reenviar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<ComprovanteDto> reenviar(@PathVariable UUID comandaUuid) {
        return ResponseEntity.ok(comprovanteService.reenviar(comandaUuid));
    }
}
