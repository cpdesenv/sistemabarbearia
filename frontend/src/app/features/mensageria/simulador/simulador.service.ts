import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DevStatus, SimularMensagemInboundRequest } from './simulador.model';

@Injectable({ providedIn: 'root' })
export class SimuladorService {
  private readonly http = inject(HttpClient);

  status(): Observable<DevStatus> {
    return this.http.get<DevStatus>('/api/dev/status');
  }

  injetarMensagem(requisicao: SimularMensagemInboundRequest): Observable<void> {
    return this.http.post<void>('/api/dev/whatsapp/inbound', requisicao);
  }

  simularFalhaNoProximoEnvio(): Observable<void> {
    return this.http.post<void>('/api/dev/whatsapp/simular-falha', {});
  }
}
