import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';

import { LoginRequest, LoginResponse, Sessao } from './auth.model';

const CHAVE_STORAGE = 'barbearia.sessao';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly sessao = signal<Sessao | null>(lerSessaoDoStorage());

  readonly usuario = computed(() => this.sessao()?.usuario ?? null);
  readonly estaAutenticado = computed(() => this.sessao() !== null);

  private renovacaoEmAndamento$: Observable<string | null> | null = null;

  login(credenciais: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', credenciais).pipe(
      tap((resposta) => this.salvarSessao(resposta)),
    );
  }

  logout(): void {
    const sessaoAtual = this.sessao();
    this.limparSessao();

    if (sessaoAtual) {
      this.http
        .post('/api/auth/logout', { refreshToken: sessaoAtual.refreshToken })
        .pipe(catchError(() => of(null)))
        .subscribe();
    }
  }

  getAccessToken(): string | null {
    return this.sessao()?.accessToken ?? null;
  }

  /** Renova o access token, compartilhando a mesma chamada entre requisicoes concorrentes que 401'aram juntas. */
  renovarAccessToken(): Observable<string | null> {
    if (this.renovacaoEmAndamento$) {
      return this.renovacaoEmAndamento$;
    }

    const refreshToken = this.sessao()?.refreshToken;
    if (!refreshToken) {
      return of(null);
    }

    this.renovacaoEmAndamento$ = this.http.post<LoginResponse>('/api/auth/refresh', { refreshToken }).pipe(
      map((resposta) => {
        this.salvarSessao(resposta);
        return resposta.accessToken;
      }),
      catchError(() => {
        this.limparSessao();
        return of(null);
      }),
      finalize(() => (this.renovacaoEmAndamento$ = null)),
      shareReplay(1),
    );

    return this.renovacaoEmAndamento$;
  }

  private salvarSessao(resposta: LoginResponse): void {
    const sessao: Sessao = {
      accessToken: resposta.accessToken,
      refreshToken: resposta.refreshToken,
      usuario: resposta.usuario,
    };
    this.sessao.set(sessao);
    localStorage.setItem(CHAVE_STORAGE, JSON.stringify(sessao));
  }

  private limparSessao(): void {
    this.sessao.set(null);
    localStorage.removeItem(CHAVE_STORAGE);
  }
}

function lerSessaoDoStorage(): Sessao | null {
  const bruto = localStorage.getItem(CHAVE_STORAGE);
  if (!bruto) {
    return null;
  }
  try {
    return JSON.parse(bruto) as Sessao;
  } catch {
    return null;
  }
}
