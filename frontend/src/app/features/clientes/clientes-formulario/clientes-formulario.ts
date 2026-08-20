import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ClienteDuplicadoResposta, SalvarClienteRequest } from '../clientes.model';
import { ClientesService } from '../clientes.service';

@Component({
  selector: 'app-clientes-formulario',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './clientes-formulario.html',
  styleUrl: './clientes-formulario.css',
})
export class ClientesFormulario {
  private readonly formBuilder = inject(FormBuilder);
  private readonly clientesService = inject(ClientesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly uuid = this.route.snapshot.paramMap.get('uuid');
  protected readonly modoEdicao = this.uuid !== null;

  protected readonly carregando = signal(this.modoEdicao);
  protected readonly salvando = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly clienteDuplicado = signal<ClienteDuplicadoResposta['clienteExistente'] | null>(null);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    nome: ['', [Validators.required]],
    telefone: ['', [Validators.required]],
    whatsapp: [''],
    cpf: [''],
    email: [''],
    logradouro: [''],
    numero: [''],
    complemento: [''],
    bairro: [''],
    cidade: [''],
    uf: [''],
    cep: [''],
    dataNascimento: [''],
    observacoes: [''],
    optInWhatsapp: [true],
    consentimentoLgpd: [false],
  });

  constructor() {
    if (this.uuid) {
      this.clientesService.obter(this.uuid).subscribe((cliente) => {
        this.formulario.setValue({
          nome: cliente.nome,
          telefone: cliente.telefone ?? '',
          whatsapp: cliente.whatsapp ?? '',
          cpf: cliente.cpf ?? '',
          email: cliente.email ?? '',
          logradouro: cliente.logradouro ?? '',
          numero: cliente.numero ?? '',
          complemento: cliente.complemento ?? '',
          bairro: cliente.bairro ?? '',
          cidade: cliente.cidade ?? '',
          uf: cliente.uf ?? '',
          cep: cliente.cep ?? '',
          dataNascimento: cliente.dataNascimento ?? '',
          observacoes: cliente.observacoes ?? '',
          optInWhatsapp: cliente.optInWhatsapp,
          consentimentoLgpd: cliente.consentimentoLgpd,
        });
        this.carregando.set(false);
      });
    }
  }

  protected salvar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.mensagemErro.set(null);
    this.clienteDuplicado.set(null);

    const valores = this.formulario.getRawValue();
    const requisicao: SalvarClienteRequest = {
      nome: valores.nome,
      telefone: valores.telefone,
      whatsapp: vazioParaNulo(valores.whatsapp),
      cpf: vazioParaNulo(valores.cpf),
      email: vazioParaNulo(valores.email),
      logradouro: vazioParaNulo(valores.logradouro),
      numero: vazioParaNulo(valores.numero),
      complemento: vazioParaNulo(valores.complemento),
      bairro: vazioParaNulo(valores.bairro),
      cidade: vazioParaNulo(valores.cidade),
      uf: vazioParaNulo(valores.uf),
      cep: vazioParaNulo(valores.cep),
      dataNascimento: vazioParaNulo(valores.dataNascimento),
      observacoes: vazioParaNulo(valores.observacoes),
      optInWhatsapp: valores.optInWhatsapp,
      consentimentoLgpd: valores.consentimentoLgpd,
    };

    const operacao = this.uuid
      ? this.clientesService.atualizar(this.uuid, requisicao)
      : this.clientesService.criar(requisicao);

    operacao.subscribe({
      next: (cliente) => this.router.navigateByUrl(`/clientes/${cliente.uuid}`),
      error: (erro: HttpErrorResponse) => {
        this.salvando.set(false);

        if (erro.status === 409) {
          const corpo = erro.error as ClienteDuplicadoResposta;
          this.clienteDuplicado.set(corpo.clienteExistente);
          this.mensagemErro.set(corpo.mensagem);
          return;
        }

        this.mensagemErro.set(
          erro.status === 400
            ? (erro.error?.mensagem ?? 'Verifique os campos destacados e tente novamente.')
            : 'Nao foi possivel salvar agora. Tente novamente em instantes.',
        );
      },
    });
  }
}

function vazioParaNulo(valor: string): string | null {
  const valorAparado = valor.trim();
  return valorAparado === '' ? null : valorAparado;
}
