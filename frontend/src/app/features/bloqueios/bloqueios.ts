import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatOptionModule } from '@angular/material/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { AuthService } from '../../core/auth/auth.service';
import { Profissional } from '../profissionais/profissionais.model';
import { ProfissionaisService } from '../profissionais/profissionais.service';
import { Bloqueio } from './bloqueios.model';
import { BloqueiosService } from './bloqueios.service';

@Component({
  selector: 'app-bloqueios',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatOptionModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './bloqueios.html',
  styleUrl: './bloqueios.css',
})
export class Bloqueios {
  private readonly bloqueiosService = inject(BloqueiosService);
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  protected readonly colunas = ['profissional', 'inicio', 'fim', 'motivo', 'acoes'];
  protected readonly bloqueios = signal<Bloqueio[]>([]);
  protected readonly profissionais = signal<Profissional[]>([]);
  protected readonly totalElementos = signal(0);
  protected readonly tamanhoPagina = signal(20);
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly filtro = this.formBuilder.nonNullable.group({
    profissionalUuid: [''],
  });

  protected readonly formulario = this.formBuilder.nonNullable.group({
    profissionalUuid: [''],
    inicio: ['', [Validators.required]],
    fim: ['', [Validators.required]],
    motivo: ['', [Validators.required]],
  });

  private pagina = 0;

  constructor() {
    this.profissionaisService.listar({ ativo: true, size: 200 }).subscribe((resposta) => {
      this.profissionais.set(resposta.content);
    });
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

  protected criar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.mensagemErro.set(null);

    const valores = this.formulario.getRawValue();
    this.bloqueiosService
      .criar({
        profissionalUuid: valores.profissionalUuid || null,
        inicio: new Date(valores.inicio).toISOString(),
        fim: new Date(valores.fim).toISOString(),
        motivo: valores.motivo,
      })
      .subscribe({
        next: () => {
          this.salvando.set(false);
          this.formulario.reset({ profissionalUuid: '', inicio: '', fim: '', motivo: '' });
          this.carregarPagina();
        },
        error: (erro: HttpErrorResponse) => {
          this.salvando.set(false);
          this.mensagemErro.set(
            erro.status === 400
              ? 'Verifique se o fim é depois do início e se todos os campos foram preenchidos.'
              : 'Nao foi possivel criar o bloqueio agora.',
          );
        },
      });
  }

  protected remover(bloqueio: Bloqueio): void {
    this.bloqueiosService.remover(bloqueio.uuid).subscribe(() => this.carregarPagina());
  }

  private carregarPagina(): void {
    this.carregando.set(true);
    const valores = this.filtro.getRawValue();

    this.bloqueiosService
      .listar({
        profissionalUuid: valores.profissionalUuid || undefined,
        page: this.pagina,
        size: this.tamanhoPagina(),
      })
      .subscribe((resposta) => {
        this.bloqueios.set(resposta.content);
        this.totalElementos.set(resposta.page.totalElements);
        this.carregando.set(false);
      });
  }
}
