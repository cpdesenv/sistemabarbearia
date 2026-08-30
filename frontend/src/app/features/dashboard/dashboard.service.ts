import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DashboardResumo } from './dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  resumo(): Observable<DashboardResumo> {
    return this.http.get<DashboardResumo>('/api/dashboard/resumo');
  }
}
