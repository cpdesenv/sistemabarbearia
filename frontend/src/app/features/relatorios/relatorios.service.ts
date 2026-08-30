import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ComparativoFaturamento, FiltroRelatorioFaturamento, RelatorioFaturamento } from './relatorios.model';

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
