package com.barbearia.barbearia.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.barbearia.domain.Barbearia;

public interface BarbeariaRepository extends JpaRepository<Barbearia, Long> {
}
