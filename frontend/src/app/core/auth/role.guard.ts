import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { Perfil } from './auth.model';

/**
 * Uso: { path: '...', canActivate: [roleGuard], data: { perfis: ['ADMIN', 'GERENTE'] } }
 */
export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const perfisPermitidos = route.data['perfis'] as Perfil[] | undefined;
  const perfilDoUsuario = authService.usuario()?.perfil;

  if (!perfisPermitidos || (perfilDoUsuario && perfisPermitidos.includes(perfilDoUsuario))) {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
