import { TestBed } from '@angular/core/testing';
import { UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { authGuard } from './auth.guard';

describe('authGuard', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => localStorage.clear());

  it('permite acesso quando ha sessao autenticada', () => {
    localStorage.setItem(
      'barbearia.sessao',
      JSON.stringify({
        accessToken: 'a',
        refreshToken: 'r',
        usuario: { uuid: 'u', nome: 'Teste', email: 't@t.com', perfil: 'ADMIN' },
      }),
    );
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    const resultado = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/dashboard' } as never),
    );

    expect(resultado).toBeTrue();
  });

  it('redireciona para /login preservando returnUrl quando nao ha sessao', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    const resultado = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/dashboard' } as never),
    ) as UrlTree;

    expect(resultado.toString()).toContain('/login');
    expect(resultado.toString()).toContain('returnUrl');
  });
});
