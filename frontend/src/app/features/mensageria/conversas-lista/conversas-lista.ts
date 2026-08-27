import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { Conversa } from '../mensageria.model';
import { MensageriaService } from '../mensageria.service';

@Component({
  selector: 'app-conversas-lista',
  imports: [DatePipe, RouterLink, MatPaginatorModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './conversas-lista.html',
  styleUrl: './conversas-lista.css',
})
export class ConversasLista {
  private readonly mensageriaService = inject(MensageriaService);

  protected readonly colunas = ['clienteNome', 'telefoneE164', 'ultimaMensagemEm'];
  protected readonly conversas = signal<Conversa[]>([]);
  protected readonly totalElementos = signal(0);
  protected readonly tamanhoPagina = signal(20);
  protected readonly carregando = signal(true);

  private pagina = 0;

  constructor() {
    this.carregarPagina();
  }

  protected mudarPagina(evento: PageEvent): void {
    this.pagina = evento.pageIndex;
    this.tamanhoPagina.set(evento.pageSize);
    this.carregarPagina();
  }

  private carregarPagina(): void {
    this.carregando.set(true);
    this.mensageriaService.listarConversas(this.pagina, this.tamanhoPagina()).subscribe((resposta) => {
      this.conversas.set(resposta.content);
      this.totalElementos.set(resposta.page.totalElements);
      this.carregando.set(false);
    });
  }
}
