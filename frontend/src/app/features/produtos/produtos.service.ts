import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  FiltroProdutos,
  PaginaMovimentos,
  PaginaProdutos,
  Produto,
  SalvarProdutoRequest,
} from './produtos.model';

@Injectable({ providedIn: 'root' })
export class ProdutosService {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroProdutos): Observable<PaginaProdutos> {
    let params = new HttpParams()
      .set('page', String(filtro.page ?? 0))
      .set('size', String(filtro.size ?? 20));

    if (filtro.nome) {
      params = params.set('nome', filtro.nome);
    }
    if (filtro.categoria) {
      params = params.set('categoria', filtro.categoria);
    }
    if (filtro.ativo !== undefined) {
      params = params.set('ativo', String(filtro.ativo));
    }

    return this.http.get<PaginaProdutos>('/api/produtos', { params });
  }

  alertasEstoqueMinimo(): Observable<Produto[]> {
    return this.http.get<Produto[]>('/api/produtos/alertas-estoque-minimo');
  }

  obter(uuid: string): Observable<Produto> {
    return this.http.get<Produto>(`/api/produtos/${uuid}`);
  }

  criar(dados: SalvarProdutoRequest): Observable<Produto> {
    return this.http.post<Produto>('/api/produtos', dados);
  }

  atualizar(uuid: string, dados: SalvarProdutoRequest): Observable<Produto> {
    return this.http.put<Produto>(`/api/produtos/${uuid}`, dados);
  }

  atualizarStatus(uuid: string, ativo: boolean): Observable<Produto> {
    return this.http.patch<Produto>(`/api/produtos/${uuid}/status`, { ativo });
  }

  entradaEstoque(
    uuid: string,
    quantidade: number,
    custoUnitario: number | null,
    motivo: string | null,
  ): Observable<Produto> {
    return this.http.post<Produto>(`/api/produtos/${uuid}/entrada-estoque`, { quantidade, custoUnitario, motivo });
  }

  ajusteEstoque(uuid: string, novaQuantidadeContada: number, motivo: string): Observable<Produto> {
    return this.http.post<Produto>(`/api/produtos/${uuid}/ajuste-estoque`, { novaQuantidadeContada, motivo });
  }

  movimentos(uuid: string, page = 0, size = 20): Observable<PaginaMovimentos> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<PaginaMovimentos>(`/api/produtos/${uuid}/movimentos`, { params });
  }
}
