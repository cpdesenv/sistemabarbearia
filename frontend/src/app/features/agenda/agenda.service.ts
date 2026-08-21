import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Agendamento, FiltroAgenda, SalvarAgendamentoRequest, SlotDisponivel } from './agenda.model';

@Injectable({ providedIn: 'root' })
export class AgendaService {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroAgenda): Observable<Agendamento[]> {
    let params = new HttpParams().set('de', filtro.de).set('ate', filtro.ate);
    if (filtro.profissionalUuid) {
      params = params.set('profissionalUuid', filtro.profissionalUuid);
    }
    if (filtro.clienteUuid) {
      params = params.set('clienteUuid', filtro.clienteUuid);
    }
    if (filtro.status) {
      params = params.set('status', filtro.status);
    }
    return this.http.get<Agendamento[]>('/api/agendamentos', { params });
  }

  obter(uuid: string): Observable<Agendamento> {
    return this.http.get<Agendamento>(`/api/agendamentos/${uuid}`);
  }

  disponibilidade(data: string, servicoUuids: string[], profissionalUuid?: string): Observable<SlotDisponivel[]> {
    let params = new HttpParams().set('data', data).set('servicoUuids', servicoUuids.join(','));
    if (profissionalUuid) {
      params = params.set('profissionalUuid', profissionalUuid);
    }
    return this.http.get<SlotDisponivel[]>('/api/agenda/disponibilidade', { params });
  }

  criar(dados: SalvarAgendamentoRequest): Observable<Agendamento> {
    return this.http.post<Agendamento>('/api/agendamentos', dados);
  }

  alterar(uuid: string, dados: SalvarAgendamentoRequest): Observable<Agendamento> {
    return this.http.put<Agendamento>(`/api/agendamentos/${uuid}`, dados);
  }

  confirmar(uuid: string): Observable<Agendamento> {
    return this.http.post<Agendamento>(`/api/agendamentos/${uuid}/confirmar`, {});
  }

  iniciar(uuid: string): Observable<Agendamento> {
    return this.http.post<Agendamento>(`/api/agendamentos/${uuid}/iniciar`, {});
  }

  finalizar(uuid: string): Observable<Agendamento> {
    return this.http.post<Agendamento>(`/api/agendamentos/${uuid}/finalizar`, {});
  }

  marcarNaoComparecimento(uuid: string): Observable<Agendamento> {
    return this.http.post<Agendamento>(`/api/agendamentos/${uuid}/nao-compareceu`, {});
  }

  cancelar(uuid: string, motivo: string): Observable<Agendamento> {
    return this.http.post<Agendamento>(`/api/agendamentos/${uuid}/cancelar`, { motivo });
  }
}
