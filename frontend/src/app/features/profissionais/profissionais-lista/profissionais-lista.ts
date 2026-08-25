import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
import { Profissional } from '../profissionais.model';
import { ProfissionaisService } from '../profissionais.service';

@Component({
  selector: 'app-profissionais-lista',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './profissionais-lista.html',
  styleUrl: './profissionais-lista.css',
})
export class ProfissionaisLista {
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  protected readonly colunas = ['cor', 'nome', 'contato', 'comissao', 'status', 'acoes'];
  protected readonly profissionais = signal<Profissional[]>([]);
  protected readonly totalElementos = signal(0);
  protected readonly tamanhoPagina = signal(20);
  protected readonly carregando = signal(true);

  protected readonly filtro = this.formBuilder.nonNullable.group({
    nome: [''],
    apenasAtivos: [false],
  });

  private pagina = 0;

  constructor() {
    this.buscar();
  }

  protected podeGerenciar(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE';
  }

  protected buscar(): void {
    this.pagina = 0;
    this.carregarPagina();
  }

  protected mudarPagina(evento: PageEvent): void {
    this.pagina = evento.pageIndex;
    this.tamanhoPagina.set(evento.pageSize);
    this.carregarPagina();
  }

  protected alternarStatus(profissional: Profissional): void {
    if (!profissional.ativo) {
      this.profissionaisService.atualizarStatus(profissional.uuid, true).subscribe(() => this.carregarPagina());
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Desativar profissional',
        message: `Desativar "${profissional.nome}"? Ele deixa de aparecer para agendamento, mas pode ser reativado depois.`,
        confirmLabel: 'Desativar',
        danger: true,
      })
      .subscribe((resultado) => {
        if (resultado.confirmed) {
          this.profissionaisService.atualizarStatus(profissional.uuid, false).subscribe(() => this.carregarPagina());
        }
      });
  }

  private carregarPagina(): void {
    this.carregando.set(true);
    const valores = this.filtro.getRawValue();

    this.profissionaisService
      .listar({
        nome: valores.nome || undefined,
        ativo: valores.apenasAtivos ? true : undefined,
        page: this.pagina,
        size: this.tamanhoPagina(),
      })
      .subscribe((resposta) => {
        this.profissionais.set(resposta.content);
        this.totalElementos.set(resposta.page.totalElements);
        this.carregando.set(false);
      });
  }
}
