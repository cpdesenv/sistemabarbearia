import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  PortalAgendamentoConfirmado,
  PortalAgendamentoRequest,
  PortalProfissional,
  PortalServico,
  PortalSlotDisponivel,
} from './portal.model';

@Injectable({ providedIn: 'root' })
export class PortalService {
  private readonly http = inject(HttpClient);

  status(): Observable<{ ativo: boolean }> {
    return this.http.get<{ ativo: boolean }>('/api/portal/status');
  }

  listarServicos(): Observable<PortalServico[]> {
    return this.http.get<PortalServico[]>('/api/portal/servicos');
  }

  listarProfissionais(servicoUuids: string[]): Observable<PortalProfissional[]> {
    const params = new HttpParams().set('servicoUuids', servicoUuids.join(','));
    return this.http.get<PortalProfissional[]>('/api/portal/profissionais', { params });
  }

  disponibilidade(data: string, servicoUuids: string[], profissionalUuid: string): Observable<PortalSlotDisponivel[]> {
    const params = new HttpParams()
      .set('data', data)
      .set('servicoUuids', servicoUuids.join(','))
      .set('profissionalUuid', profissionalUuid);
    return this.http.get<PortalSlotDisponivel[]>('/api/portal/disponibilidade', { params });
  }

  criarAgendamento(dados: PortalAgendamentoRequest): Observable<PortalAgendamentoConfirmado> {
    return this.http.post<PortalAgendamentoConfirmado>('/api/portal/agendamentos', dados);
  }
}
