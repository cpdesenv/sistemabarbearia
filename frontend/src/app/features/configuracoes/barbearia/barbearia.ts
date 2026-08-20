import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { BarbeariaService } from './barbearia.service';
import { AtualizarBarbeariaRequest, Barbearia } from './barbearia.model';

@Component({
  selector: 'app-barbearia-config',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './barbearia.html',
  styleUrl: './barbearia.css',
})
export class BarbeariaConfig {
  private readonly formBuilder = inject(FormBuilder);
  private readonly barbeariaService = inject(BarbeariaService);

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly mensagemSucesso = signal<string | null>(null);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    nome: ['', [Validators.required]],
    cnpj: [''],
    telefone: [''],
    email: [''],
    logradouro: [''],
    numero: [''],
    complemento: [''],
    bairro: [''],
    cidade: [''],
    uf: [''],
    cep: [''],
    fusoHorario: ['', [Validators.required]],
    antecedenciaMinimaAgendamentoMinutos: [0, [Validators.required, Validators.min(0)]],
    antecedenciaMaximaAgendamentoDias: [1, [Validators.required, Validators.min(1)]],
    antecedenciaMinimaCancelamentoMinutos: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    this.barbeariaService.obter().subscribe({
      next: (barbearia) => {
        this.preencherFormulario(barbearia);
        this.carregando.set(false);
      },
      error: () => {
        this.mensagemErro.set('Nao foi possivel carregar os dados da barbearia.');
        this.carregando.set(false);
      },
    });
  }

  protected salvar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.mensagemSucesso.set(null);
    this.mensagemErro.set(null);

    const valores = this.formulario.getRawValue();
    const requisicao: AtualizarBarbeariaRequest = {
      ...valores,
      cnpj: valorOuNulo(valores.cnpj),
      telefone: valorOuNulo(valores.telefone),
      email: valorOuNulo(valores.email),
      logradouro: valorOuNulo(valores.logradouro),
      numero: valorOuNulo(valores.numero),
      complemento: valorOuNulo(valores.complemento),
      bairro: valorOuNulo(valores.bairro),
      cidade: valorOuNulo(valores.cidade),
      uf: valorOuNulo(valores.uf),
      cep: valorOuNulo(valores.cep),
    };

    this.barbeariaService.atualizar(requisicao).subscribe({
      next: (barbearia) => {
        this.preencherFormulario(barbearia);
        this.salvando.set(false);
        this.mensagemSucesso.set('Dados salvos com sucesso.');
      },
      error: (erro: HttpErrorResponse) => {
        this.salvando.set(false);
        this.mensagemErro.set(
          erro.status === 400
            ? 'Verifique os campos destacados e tente novamente.'
            : 'Nao foi possivel salvar agora. Tente novamente em instantes.',
        );
      },
    });
  }

  private preencherFormulario(barbearia: Barbearia): void {
    this.formulario.setValue({
      nome: barbearia.nome,
      cnpj: barbearia.cnpj ?? '',
      telefone: barbearia.telefone ?? '',
      email: barbearia.email ?? '',
      logradouro: barbearia.logradouro ?? '',
      numero: barbearia.numero ?? '',
      complemento: barbearia.complemento ?? '',
      bairro: barbearia.bairro ?? '',
      cidade: barbearia.cidade ?? '',
      uf: barbearia.uf ?? '',
      cep: barbearia.cep ?? '',
      fusoHorario: barbearia.fusoHorario,
      antecedenciaMinimaAgendamentoMinutos: barbearia.antecedenciaMinimaAgendamentoMinutos,
      antecedenciaMaximaAgendamentoDias: barbearia.antecedenciaMaximaAgendamentoDias,
      antecedenciaMinimaCancelamentoMinutos: barbearia.antecedenciaMinimaCancelamentoMinutos,
    });
  }
}

function valorOuNulo(valor: string): string | null {
  return valor.trim() === '' ? null : valor.trim();
}
