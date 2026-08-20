import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { Login } from './login';

describe('Login', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Login, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('deve criar o componente', () => {
    const fixture = TestBed.createComponent(Login);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('nao deve chamar o backend quando o formulario e invalido', () => {
    const fixture = TestBed.createComponent(Login);
    const login = fixture.componentInstance;

    (login as unknown as { entrar: () => void }).entrar();

    expect(() => httpMock.expectNone('/api/auth/login')).not.toThrow();
  });

  it('deve chamar o backend quando o formulario e valido', () => {
    const fixture = TestBed.createComponent(Login);
    const login = fixture.componentInstance;
    (login as unknown as { formulario: { setValue: (v: unknown) => void } }).formulario.setValue({
      email: 'barbeiro@teste.com',
      senha: 'SenhaForte123!',
    });

    (login as unknown as { entrar: () => void }).entrar();

    expect(httpMock.expectOne('/api/auth/login').request.method).toBe('POST');
  });
});
