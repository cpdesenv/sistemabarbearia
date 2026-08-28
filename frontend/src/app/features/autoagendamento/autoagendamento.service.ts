import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AgendamentoConfirmado,
  ConfiguracaoAutoagendamento,
  CriarAutoagendamentoRequest,
  ProfissionalPublico,
  ServicoPublico,
  SlotDisponivel,
} from './autoagendamento.model';

@Injectable({ providedIn: 'root' })
export class AutoagendamentoService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/autoagendamento';

  obterConfiguracao(): Observable<ConfiguracaoAutoagendamento> {
    return this.http.get<ConfiguracaoAutoagendamento>(`${this.base}/configuracao`);
  }

  consultarServicos(): Observable<ServicoPublico[]> {
    return this.http.get<ServicoPublico[]>(`${this.base}/servicos`);
  }

  consultarProfissionais(): Observable<ProfissionalPublico[]> {
    return this.http.get<ProfissionalPublico[]>(`${this.base}/profissionais`);
  }

  consultarDisponibilidade(
    data: string,
    servicoUuids: string[],
    profissionalUuid: string,
  ): Observable<SlotDisponivel[]> {
    const params = new URLSearchParams();
    params.set('data', data);
    servicoUuids.forEach((uuid) => params.append('servicoUuids', uuid));
    params.set('profissionalUuid', profissionalUuid);
    return this.http.get<SlotDisponivel[]>(`${this.base}/disponibilidade?${params.toString()}`);
  }

  agendar(requisicao: CriarAutoagendamentoRequest): Observable<AgendamentoConfirmado> {
    return this.http.post<AgendamentoConfirmado>(this.base, requisicao);
  }
}
