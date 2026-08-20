import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { Shell } from './shell';

function configurarComSessao(perfil: string): void {
  localStorage.setItem(
    'barbearia.sessao',
    JSON.stringify({
      accessToken: 'a',
      refreshToken: 'r',
      usuario: { uuid: 'u', nome: 'Teste', email: 't@t.com', perfil },
    }),
  );
}

async function criarShell(): Promise<HTMLElement> {
  await TestBed.configureTestingModule({
    imports: [Shell],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  }).compileComponents();

  const fixture = TestBed.createComponent(Shell);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function rotulosDoMenu(elemento: HTMLElement): string[] {
  return Array.from(elemento.querySelectorAll('.sidebar__item')).map((item) => item.textContent?.trim() ?? '');
}

describe('Shell', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('esconde Configurações do menu para o perfil GERENTE', async () => {
    configurarComSessao('GERENTE');
    const rotulos = rotulosDoMenu(await criarShell());

    expect(rotulos).toContain('Dashboard');
    expect(rotulos).not.toContain('Configurações');
  });

  it('esconde Configurações do menu para o perfil BARBEIRO', async () => {
    configurarComSessao('BARBEIRO');
    const rotulos = rotulosDoMenu(await criarShell());

    expect(rotulos).not.toContain('Configurações');
  });

  it('mostra Configurações do menu para o perfil ADMIN', async () => {
    configurarComSessao('ADMIN');
    const rotulos = rotulosDoMenu(await criarShell());

    expect(rotulos).toContain('Configurações');
  });
});
