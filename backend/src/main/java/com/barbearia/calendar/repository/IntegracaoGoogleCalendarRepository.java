package com.barbearia.calendar.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.calendar.domain.IntegracaoGoogleCalendar;

public interface IntegracaoGoogleCalendarRepository extends JpaRepository<IntegracaoGoogleCalendar, Long> {
}
