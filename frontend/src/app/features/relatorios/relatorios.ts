import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatOptionModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { GraficoBarras } from '../dashboard/graficos/grafico-barras/grafico-barras';
import { GraficoRosca } from '../dashboard/graficos/grafico-rosca/grafico-rosca';
import { FormaPagamento, RUTULOS_FORMA_PAGAMENTO } from '../financeiro/financeiro.model';
import { Profissional } from '../profissionais/profissionais.model';
import { ProfissionaisService } from '../profissionais/profissionais.service';
import { Servico } from '../servicos/servicos.model';
import { ServicosService } from '../servicos/servicos.service';
import {
  ComparativoFaturamento,
  RelatorioAgenda,
  RelatorioClientes,
  RelatorioFaturamento,
  RelatorioHeatmap,
  RelatorioProduto,
} from './relatorios.model';
import { RelatoriosService } from './relatorios.service';

const NOMES_DIA_SEMANA = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
const HORAS_DO_DIA = Array.from({ length: 24 }, (_, hora) => hora);

/** Formata em fuso local (yyyy-MM-dd) — Date#toISOString converteria para UTC e poderia virar o dia. */
function formatarDataLocal(data: Date): string {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

function primeiroDiaDoMes(): string {
  const hoje = new Date();
  return formatarDataLocal(new Date(hoje.getFullYear(), hoje.getMonth(), 1));
}

function hojeIso(): string {
  return formatarDataLocal(new Date());
}

function mesAtualIso(): string {
  return hojeIso().substring(0, 7);
}

@Component({
  selector: 'app-relatorios',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatOptionModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    GraficoBarras,
    GraficoRosca,
  ],
  templateUrl: './relatorios.html',
  styleUrl: './relatorios.css',
})
export class Relatorios {
  private readonly relatoriosService = inject(RelatoriosService);
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly servicosService = inject(ServicosService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly rotulosFormaPagamento = RUTULOS_FORMA_PAGAMENTO;
  protected readonly formasPagamento: FormaPagamento[] = ['DINHEIRO', 'CARTAO_DEBITO', 'CARTAO_CREDITO', 'PIX', 'OUTRO'];

  protected readonly profissionais = signal<Profissional[]>([]);
  protected readonly servicos = signal<Servico[]>([]);
  protected readonly relatorio = signal<RelatorioFaturamento | null>(null);
  protected readonly comparativo = signal<ComparativoFaturamento | null>(null);
  protected readonly agenda = signal<RelatorioAgenda | null>(null);
  protected readonly clientes = signal<RelatorioClientes | null>(null);
  protected readonly produtos = signal<RelatorioProduto | null>(null);
  protected readonly heatmap = signal<RelatorioHeatmap | null>(null);
  protected readonly carregando = signal(true);

  protected readonly nomesDiaSemana = NOMES_DIA_SEMANA;
  protected readonly horasDoDia = HORAS_DO_DIA;

  protected readonly filtro = this.formBuilder.nonNullable.group({
    dataInicial: [primeiroDiaDoMes()],
    dataFinal: [hojeIso()],
    profissionalUuid: [''],
    servicoUuid: [''],
    formaPagamento: [''],
  });

  constructor() {
    this.profissionaisService.listar({ ativo: true, size: 200 }).subscribe((resposta) => {
      this.profissionais.set(resposta.content);
    });
    this.servicosService.listar({ ativo: true, size: 200 }).subscribe((resposta) => {
      this.servicos.set(resposta.content);
    });
    this.buscar();
  }

  protected buscar(): void {
    this.carregando.set(true);
    const valores = this.filtro.getRawValue();
    const filtroComuns = {
      profissionalUuid: valores.profissionalUuid || undefined,
      servicoUuid: valores.servicoUuid || undefined,
      formaPagamento: (valores.formaPagamento || undefined) as FormaPagamento | undefined,
    };

    this.relatoriosService
      .faturamento({ dataInicial: valores.dataInicial, dataFinal: valores.dataFinal, ...filtroComuns })
      .subscribe({
        next: (relatorio) => {
          this.relatorio.set(relatorio);
          this.carregando.set(false);
        },
        error: () => this.carregando.set(false),
      });

    this.relatoriosService
      .comparativoFaturamento(mesAtualIso(), { dataInicial: valores.dataInicial, dataFinal: valores.dataFinal, ...filtroComuns })
      .subscribe((comparativo) => this.comparativo.set(comparativo));

    this.relatoriosService
      .agenda({ dataInicial: valores.dataInicial, dataFinal: valores.dataFinal, profissionalUuid: filtroComuns.profissionalUuid })
      .subscribe((agenda) => this.agenda.set(agenda));

    this.relatoriosService
      .clientes(valores.dataInicial, valores.dataFinal)
      .subscribe((clientes) => this.clientes.set(clientes));

    this.relatoriosService
      .produtos(valores.dataInicial, valores.dataFinal)
      .subscribe((produtos) => this.produtos.set(produtos));

    this.relatoriosService
      .heatmapHorarios(valores.dataInicial, valores.dataFinal)
      .subscribe((heatmap) => this.heatmap.set(heatmap));
  }

  protected paraGraficoBarras(linhas: { nome: string; valorTotal: number }[]): { nome: string; quantidade: number }[] {
    return linhas.map((linha) => ({ nome: linha.nome, quantidade: linha.valorTotal }));
  }

  /** Quantidade de finalizados numa celula do heatmap (0 se nao houver dado para o dia x hora). */
  protected quantidadeCelula(diaSemana: number, hora: number): number {
    const celula = this.heatmap()?.celulas.find((c) => c.diaSemana === diaSemana && c.hora === hora);
    return celula?.quantidadeFinalizados ?? 0;
  }

  /** Intensidade de 0 a 1 relativa ao maior valor do heatmap atual, para escala de cor no template. */
  protected intensidadeCelula(quantidade: number): number {
    const maximo = Math.max(1, ...(this.heatmap()?.celulas.map((c) => c.quantidadeFinalizados) ?? [0]));
    return quantidade / maximo;
  }
}
