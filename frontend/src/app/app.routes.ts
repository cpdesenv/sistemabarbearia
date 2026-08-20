import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  {
    path: '',
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard) },
      {
        path: 'configuracoes/barbearia',
        loadComponent: () =>
          import('./features/configuracoes/barbearia/barbearia').then((m) => m.BarbeariaConfig),
        canActivate: [roleGuard],
        data: { perfis: ['ADMIN'] }
      },
      {
        path: 'servicos',
        loadComponent: () =>
          import('./features/servicos/servicos-lista/servicos-lista').then((m) => m.ServicosLista)
      },
      {
        path: 'servicos/novo',
        loadComponent: () =>
          import('./features/servicos/servicos-formulario/servicos-formulario').then((m) => m.ServicosFormulario),
        canActivate: [roleGuard],
        data: { perfis: ['ADMIN', 'GERENTE'] }
      },
      {
        path: 'servicos/:uuid/editar',
        loadComponent: () =>
          import('./features/servicos/servicos-formulario/servicos-formulario').then((m) => m.ServicosFormulario),
        canActivate: [roleGuard],
        data: { perfis: ['ADMIN', 'GERENTE'] }
      },
      {
        path: 'profissionais',
        loadComponent: () =>
          import('./features/profissionais/profissionais-lista/profissionais-lista').then(
            (m) => m.ProfissionaisLista
          )
      },
      {
        path: 'profissionais/novo',
        loadComponent: () =>
          import('./features/profissionais/profissionais-formulario/profissionais-formulario').then(
            (m) => m.ProfissionaisFormulario
          ),
        canActivate: [roleGuard],
        data: { perfis: ['ADMIN', 'GERENTE'] }
      },
      {
        path: 'profissionais/:uuid/editar',
        loadComponent: () =>
          import('./features/profissionais/profissionais-formulario/profissionais-formulario').then(
            (m) => m.ProfissionaisFormulario
          ),
        canActivate: [roleGuard],
        data: { perfis: ['ADMIN', 'GERENTE'] }
      },
      {
        path: 'bloqueios',
        loadComponent: () => import('./features/bloqueios/bloqueios').then((m) => m.Bloqueios)
      },
      {
        path: 'clientes',
        loadComponent: () =>
          import('./features/clientes/clientes-lista/clientes-lista').then((m) => m.ClientesLista)
      },
      {
        path: 'clientes/novo',
        loadComponent: () =>
          import('./features/clientes/clientes-formulario/clientes-formulario').then(
            (m) => m.ClientesFormulario
          ),
        canActivate: [roleGuard],
        data: { perfis: ['ADMIN', 'GERENTE', 'RECEPCAO'] }
      },
      {
        path: 'clientes/:uuid',
        loadComponent: () =>
          import('./features/clientes/clientes-ficha/clientes-ficha').then((m) => m.ClientesFicha)
      },
      {
        path: 'clientes/:uuid/editar',
        loadComponent: () =>
          import('./features/clientes/clientes-formulario/clientes-formulario').then(
            (m) => m.ClientesFormulario
          ),
        canActivate: [roleGuard],
        data: { perfis: ['ADMIN', 'GERENTE', 'RECEPCAO'] }
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
