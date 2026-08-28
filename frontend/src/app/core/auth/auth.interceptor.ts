import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

const ROTAS_SEM_TOKEN = ['/api/auth/', '/api/autoagendamento'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const ehRotaPublica = ROTAS_SEM_TOKEN.some((rota) => req.url.startsWith(rota));
  const accessToken = authService.getAccessToken();

  const requisicao = !ehRotaPublica && accessToken
    ? req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
    : req;

  return next(requisicao).pipe(
    catchError((erro: unknown) => {
      const foi401 = erro instanceof HttpErrorResponse && erro.status === 401;

      if (!foi401 || ehRotaPublica) {
        return throwError(() => erro);
      }

      return authService.renovarAccessToken().pipe(
        switchMap((novoAccessToken) => {
          if (!novoAccessToken) {
            router.navigateByUrl('/login');
            return throwError(() => erro);
          }
          const requisicaoRenovada = req.clone({
            setHeaders: { Authorization: `Bearer ${novoAccessToken}` },
          });
          return next(requisicaoRenovada);
        }),
      );
    }),
  );
};
