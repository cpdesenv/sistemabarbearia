package com.barbearia.agenda.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.dto.SlotDisponivelDto;
import com.barbearia.agenda.service.AvailabilityService;

@RestController
@RequestMapping("/api/agenda")
@RequiredArgsConstructor
@Tag(name = "Agenda")
public class DisponibilidadeController {

    private final AvailabilityService availabilityService;

    @GetMapping("/disponibilidade")
    public List<SlotDisponivelDto> consultarDisponibilidade(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam List<UUID> servicoUuids,
            @RequestParam(required = false) UUID profissionalUuid) {
        return availabilityService.consultarDisponibilidade(data, servicoUuids, profissionalUuid);
    }
}
