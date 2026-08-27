import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatTableModule } from '@angular/material/table';

import { GoogleCalendarService } from './google-calendar.service';
import { AgendamentoForaDeSincronia, ModoCalendario, StatusIntegracaoGoogleCalendar } from './google-calendar.model';

@Component({
  selector: 'app-google-calendar-config',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    MatTableModule,
  ],
  templateUrl: './google-calendar.html',
  styleUrl: './google-calendar.css',
})
export class GoogleCalendarConfig {
  private readonly formBuilder = inject(FormBuilder);
  private readonly googleCalendarService = inject(GoogleCalendarService);
  private readonly activatedRoute = inject(ActivatedRoute);

  protected readonly carregando = signal(true);
  protected readonly conectando = signal(false);
  protected readonly salvandoModo = signal(false);
  protected readonly ressincronizando = signal(false);
  protected readonly mensagemSucesso = signal<string | null>(null);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly status = signal<StatusIntegracaoGoogleCalendar | null>(null);
  protected readonly foraDeSincronia = signal<AgendamentoForaDeSincronia[]>([]);

  protected readonly colunasForaDeSincronia = ['clienteNome', 'inicioAgendamento', 'tipoOperacao', 'tentativas', 'ultimoErro'];

  protected readonly formularioModo = this.formBuilder.nonNullable.group({
    modo: ['CALENDARIO_UNICO' as ModoCalendario, [Validators.required]],
    calendarioIdUnico: [''],
  });

  constructor() {
    const parametros = this.activatedRoute.snapshot.queryParamMap;
    if (parametros.get('conectado') === 'true') {
      this.mensagemSucesso.set('Google Calendar conectado com sucesso.');
    } else if (parametros.get('erro')) {
      this.mensagemErro.set('Nao foi possivel concluir a conexao com o Google. Tente novamente.');
    }

    this.carregar();
  }

  protected conectar(): void {
    this.conectando.set(true);
    this.mensagemErro.set(null);
    this.googleCalendarService.conectar().subscribe({
      next: (resposta) => {
        window.location.href = resposta.url;
      },
      error: () => {
        this.conectando.set(false);
        this.mensagemErro.set('Nao foi possivel iniciar a conexao com o Google. Tente novamente.');
      },
    });
  }

  protected desconectar(): void {
    this.googleCalendarService.desconectar().subscribe({
      next: () => {
        this.mensagemSucesso.set('Google Calendar desconectado.');
        this.carregar();
      },
      error: () => this.mensagemErro.set('Nao foi possivel desconectar agora. Tente novamente.'),
    });
  }

  protected salvarModo(): void {
    if (this.formularioModo.invalid) {
      this.formularioModo.markAllAsTouched();
      return;
    }

    this.salvandoModo.set(true);
    this.mensagemErro.set(null);
    const valores = this.formularioModo.getRawValue();

    this.googleCalendarService
      .atualizarModo({
        modo: valores.modo,
        calendarioIdUnico: valores.calendarioIdUnico.trim() === '' ? null : valores.calendarioIdUnico.trim(),
      })
      .subscribe({
        next: () => {
          this.salvandoModo.set(false);
          this.mensagemSucesso.set('Configuracao de calendario salva.');
        },
        error: (erro: HttpErrorResponse) => {
          this.salvandoModo.set(false);
          this.mensagemErro.set(
            erro.status === 400
              ? 'Verifique os campos destacados e tente novamente.'
              : 'Nao foi possivel salvar agora. Tente novamente em instantes.',
          );
        },
      });
  }

  protected ressincronizar(): void {
    this.ressincronizando.set(true);
    this.mensagemErro.set(null);
    this.googleCalendarService.ressincronizar().subscribe({
      next: () => {
        this.ressincronizando.set(false);
        this.mensagemSucesso.set('Ressincronizacao solicitada.');
        this.carregarForaDeSincronia();
      },
      error: () => {
        this.ressincronizando.set(false);
        this.mensagemErro.set('Nao foi possivel ressincronizar agora. Tente novamente.');
      },
    });
  }

  private carregar(): void {
    this.googleCalendarService.obterStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        this.formularioModo.setValue({
          modo: status.modo,
          calendarioIdUnico: status.calendarioIdUnico ?? '',
        });
        this.carregando.set(false);
        this.carregarForaDeSincronia();
      },
      error: () => {
        this.carregando.set(false);
        this.mensagemErro.set('Nao foi possivel carregar o status da integracao.');
      },
    });
  }

  private carregarForaDeSincronia(): void {
    this.googleCalendarService.listarForaDeSincronia().subscribe({
      next: (lista) => this.foraDeSincronia.set(lista),
      error: () => this.foraDeSincronia.set([]),
    });
  }
}
