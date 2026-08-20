import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from './auth.service';
import { LoginResponse } from './auth.model';

const RESPOSTA_LOGIN: LoginResponse = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiraEmSegundos: 900,
  usuario: { uuid: 'uuid-1', nome: 'Barbeiro Teste', email: 'barbeiro@teste.com', perfil: 'BARBEIRO' },
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('deve salvar a sessao e ficar autenticado apos login', () => {
    service.login({ email: 'barbeiro@teste.com', senha: 'SenhaForte123!' }).subscribe();

    const requisicao = httpMock.expectOne('/api/auth/login');
    requisicao.flush(RESPOSTA_LOGIN);

    expect(service.estaAutenticado()).toBeTrue();
    expect(service.usuario()?.email).toBe('barbeiro@teste.com');
    expect(service.getAccessToken()).toBe('access-1');
    expect(localStorage.getItem('barbearia.sessao')).toContain('access-1');
  });

  it('deve limpar a sessao ao fazer logout', () => {
    service.login({ email: 'barbeiro@teste.com', senha: 'SenhaForte123!' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(RESPOSTA_LOGIN);

    service.logout();
    httpMock.expectOne('/api/auth/logout').flush({});

    expect(service.estaAutenticado()).toBeFalse();
    expect(service.getAccessToken()).toBeNull();
    expect(localStorage.getItem('barbearia.sessao')).toBeNull();
  });

  it('nao deve disparar duas chamadas de refresh quando ha renovacao concorrente', () => {
    service.login({ email: 'barbeiro@teste.com', senha: 'SenhaForte123!' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(RESPOSTA_LOGIN);

    const tokensRecebidos: (string | null)[] = [];
    service.renovarAccessToken().subscribe((token) => tokensRecebidos.push(token));
    service.renovarAccessToken().subscribe((token) => tokensRecebidos.push(token));

    const requisicoes = httpMock.match('/api/auth/refresh');
    expect(requisicoes.length).toBe(1);
    requisicoes[0].flush({ ...RESPOSTA_LOGIN, accessToken: 'access-2', refreshToken: 'refresh-2' });

    expect(tokensRecebidos).toEqual(['access-2', 'access-2']);
  });

  it('deve limpar a sessao quando o refresh falha', () => {
    service.login({ email: 'barbeiro@teste.com', senha: 'SenhaForte123!' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(RESPOSTA_LOGIN);

    const tokensRecebidos: (string | null)[] = [];
    service.renovarAccessToken().subscribe((token) => tokensRecebidos.push(token));

    httpMock.expectOne('/api/auth/refresh').flush('erro', { status: 401, statusText: 'Unauthorized' });

    expect(tokensRecebidos).toEqual([null]);
    expect(service.estaAutenticado()).toBeFalse();
  });
});
