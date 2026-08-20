import { TestBed } from '@angular/core/testing';
import { UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { roleGuard } from './role.guard';

function configurarComSessao(perfil: string): void {
  localStorage.setItem(
    'barbearia.sessao',
    JSON.stringify({
      accessToken: 'a',
      refreshToken: 'r',
      usuario: { uuid: 'u', nome: 'Teste', email: 't@t.com', perfil },
    }),
  );
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
}

describe('roleGuard', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('permite acesso quando o perfil do usuario esta na lista permitida', () => {
    configurarComSessao('ADMIN');

    const resultado = TestBed.runInInjectionContext(() =>
      roleGuard({ data: { perfis: ['ADMIN', 'GERENTE'] } } as never, {} as never),
    );

    expect(resultado).toBeTrue();
  });

  it('redireciona quando o perfil do usuario nao esta na lista permitida', () => {
    configurarComSessao('BARBEIRO');

    const resultado = TestBed.runInInjectionContext(() =>
      roleGuard({ data: { perfis: ['ADMIN', 'GERENTE'] } } as never, {} as never),
    ) as UrlTree;

    expect(resultado.toString()).toContain('/dashboard');
  });

  it('permite acesso quando a rota nao restringe perfis', () => {
    configurarComSessao('RECEPCAO');

    const resultado = TestBed.runInInjectionContext(() => roleGuard({ data: {} } as never, {} as never));

    expect(resultado).toBeTrue();
  });
});
