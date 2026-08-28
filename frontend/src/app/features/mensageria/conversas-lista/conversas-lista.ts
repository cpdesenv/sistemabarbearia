import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { Conversa, ModoAtendimento } from '../mensageria.model';
import { MensageriaService } from '../mensageria.service';

@Component({
  selector: 'app-conversas-lista',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    MatFormFieldModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './conversas-lista.html',
  styleUrl: './conversas-lista.css',
})
export class ConversasLista {
  private readonly mensageriaService = inject(MensageriaService);

  protected readonly colunas = [
    'clienteNome',
    'telefoneE164',
    'modoAtendimento',
    'custoLlmAcumuladoCentavos',
    'ultimaMensagemEm',
  ];
  protected readonly conversas = signal<Conversa[]>([]);
  protected readonly totalElementos = signal(0);
  protected readonly tamanhoPagina = signal(20);
  protected readonly carregando = signal(true);
  protected readonly filtroStatus = signal<ModoAtendimento | ''>('');

  private pagina = 0;

  constructor() {
    this.carregarPagina();
  }

  protected mudarPagina(evento: PageEvent): void {
    this.pagina = evento.pageIndex;
    this.tamanhoPagina.set(evento.pageSize);
    this.carregarPagina();
  }

  protected filtrarPorStatus(status: ModoAtendimento | ''): void {
    this.filtroStatus.set(status);
    this.pagina = 0;
    this.carregarPagina();
  }

  private carregarPagina(): void {
    this.carregando.set(true);
    const status = this.filtroStatus() || undefined;
    this.mensageriaService.listarConversas(this.pagina, this.tamanhoPagina(), status).subscribe((resposta) => {
      this.conversas.set(resposta.content);
      this.totalElementos.set(resposta.page.totalElements);
      this.carregando.set(false);
    });
  }
}
