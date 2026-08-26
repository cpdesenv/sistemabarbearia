import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Assinatura,
  AssinaturaResumo,
  PlanoAssinatura,
  SalvarPlanoAssinaturaRequest,
  StatusAssinatura,
} from './assinaturas.model';

@Injectable({ providedIn: 'root' })
export class AssinaturasService {
  private readonly http = inject(HttpClient);

  listarPlanos(ativo?: boolean): Observable<PlanoAssinatura[]> {
    let params = new HttpParams();
    if (ativo !== undefined) {
      params = params.set('ativo', String(ativo));
    }
    return this.http.get<PlanoAssinatura[]>('/api/planos-assinatura', { params });
  }

  criarPlano(dados: SalvarPlanoAssinaturaRequest): Observable<PlanoAssinatura> {
    return this.http.post<PlanoAssinatura>('/api/planos-assinatura', dados);
  }

  atualizarStatusPlano(uuid: string, ativo: boolean): Observable<PlanoAssinatura> {
    return this.http.patch<PlanoAssinatura>(`/api/planos-assinatura/${uuid}/status`, { ativo });
  }

  listarAssinaturas(status?: StatusAssinatura): Observable<Assinatura[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Assinatura[]>('/api/assinaturas', { params });
  }

  resumo(): Observable<AssinaturaResumo> {
    return this.http.get<AssinaturaResumo>('/api/assinaturas/resumo');
  }

  assinar(clienteUuid: string, planoUuid: string): Observable<Assinatura> {
    return this.http.post<Assinatura>('/api/assinaturas', { clienteUuid, planoUuid });
  }

  cancelar(uuid: string, motivo: string, dataEfeito: string): Observable<Assinatura> {
    return this.http.post<Assinatura>(`/api/assinaturas/${uuid}/cancelar`, { motivo, dataEfeito });
  }
}
