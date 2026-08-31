import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ComparativoFaturamento,
  FiltroRelatorioFaturamento,
  RelatorioAgenda,
  RelatorioClientes,
  RelatorioFaturamento,
  RelatorioHeatmap,
  RelatorioPrevisao,
  RelatorioProduto,
} from './relatorios.model';

@Injectable({ providedIn: 'root' })
export class RelatoriosService {
  private readonly http = inject(HttpClient);

  faturamento(filtro: FiltroRelatorioFaturamento): Observable<RelatorioFaturamento> {
    const params = this.paramsComuns(filtro)
      .set('dataInicial', filtro.dataInicial)
      .set('dataFinal', filtro.dataFinal);
    return this.http.get<RelatorioFaturamento>('/api/relatorios/faturamento', { params });
  }

  comparativoFaturamento(mes: string, filtro: FiltroRelatorioFaturamento): Observable<ComparativoFaturamento> {
    const params = this.paramsComuns(filtro).set('mes', mes);
    return this.http.get<ComparativoFaturamento>('/api/relatorios/faturamento/comparativo', { params });
  }

  agenda(filtro: FiltroRelatorioFaturamento): Observable<RelatorioAgenda> {
    let params = new HttpParams().set('dataInicial', filtro.dataInicial).set('dataFinal', filtro.dataFinal);
    if (filtro.profissionalUuid) {
      params = params.set('profissionalUuid', filtro.profissionalUuid);
    }
    return this.http.get<RelatorioAgenda>('/api/relatorios/agenda', { params });
  }

  clientes(dataInicial: string, dataFinal: string): Observable<RelatorioClientes> {
    const params = new HttpParams().set('dataInicial', dataInicial).set('dataFinal', dataFinal);
    return this.http.get<RelatorioClientes>('/api/relatorios/clientes', { params });
  }

  produtos(dataInicial: string, dataFinal: string): Observable<RelatorioProduto> {
    const params = new HttpParams().set('dataInicial', dataInicial).set('dataFinal', dataFinal);
    return this.http.get<RelatorioProduto>('/api/relatorios/produtos', { params });
  }

  heatmapHorarios(dataInicial: string, dataFinal: string): Observable<RelatorioHeatmap> {
    const params = new HttpParams().set('dataInicial', dataInicial).set('dataFinal', dataFinal);
    return this.http.get<RelatorioHeatmap>('/api/relatorios/heatmap-horarios', { params });
  }

  previsaoCompromissos(): Observable<RelatorioPrevisao> {
    return this.http.get<RelatorioPrevisao>('/api/relatorios/previsao-compromissos');
  }

  private paramsComuns(filtro: FiltroRelatorioFaturamento): HttpParams {
    let params = new HttpParams();
    if (filtro.profissionalUuid) {
      params = params.set('profissionalUuid', filtro.profissionalUuid);
    }
    if (filtro.servicoUuid) {
      params = params.set('servicoUuid', filtro.servicoUuid);
    }
    if (filtro.formaPagamento) {
      params = params.set('formaPagamento', filtro.formaPagamento);
    }
    return params;
  }
}
