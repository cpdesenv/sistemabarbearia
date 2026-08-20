import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AssociarServicoRequest,
  FiltroProfissionais,
  JanelaHorario,
  PaginaProfissionais,
  Profissional,
  SalvarProfissionalRequest,
  ServicoVinculado,
} from './profissionais.model';

@Injectable({ providedIn: 'root' })
export class ProfissionaisService {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroProfissionais): Observable<PaginaProfissionais> {
    let params = new HttpParams()
      .set('page', String(filtro.page ?? 0))
      .set('size', String(filtro.size ?? 20));

    if (filtro.nome) {
      params = params.set('nome', filtro.nome);
    }
    if (filtro.ativo !== undefined) {
      params = params.set('ativo', String(filtro.ativo));
    }

    return this.http.get<PaginaProfissionais>('/api/profissionais', { params });
  }

  obter(uuid: string): Observable<Profissional> {
    return this.http.get<Profissional>(`/api/profissionais/${uuid}`);
  }

  criar(dados: SalvarProfissionalRequest): Observable<Profissional> {
    return this.http.post<Profissional>('/api/profissionais', dados);
  }

  atualizar(uuid: string, dados: SalvarProfissionalRequest): Observable<Profissional> {
    return this.http.put<Profissional>(`/api/profissionais/${uuid}`, dados);
  }

  atualizarStatus(uuid: string, ativo: boolean): Observable<Profissional> {
    return this.http.patch<Profissional>(`/api/profissionais/${uuid}/status`, { ativo });
  }

  listarServicosVinculados(uuid: string): Observable<ServicoVinculado[]> {
    return this.http.get<ServicoVinculado[]>(`/api/profissionais/${uuid}/servicos`);
  }

  sincronizarServicos(uuid: string, vinculos: AssociarServicoRequest[]): Observable<ServicoVinculado[]> {
    return this.http.put<ServicoVinculado[]>(`/api/profissionais/${uuid}/servicos`, vinculos);
  }

  obterGradeHoraria(uuid: string): Observable<JanelaHorario[]> {
    return this.http.get<JanelaHorario[]>(`/api/profissionais/${uuid}/grade-horaria`);
  }

  sincronizarGradeHoraria(uuid: string, janelas: JanelaHorario[]): Observable<JanelaHorario[]> {
    return this.http.put<JanelaHorario[]>(`/api/profissionais/${uuid}/grade-horaria`, janelas);
  }
}
