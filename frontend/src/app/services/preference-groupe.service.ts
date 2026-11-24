
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export type StatutPreference = 'ACCEPTE' | 'REFUSE';

export interface PreferenceGroupe {
  id: number;
  professeurId: number;
  groupeId: number;
  groupeNom: string;
  statut: StatutPreference;
}

@Injectable({
  providedIn: 'root'
})
export class PreferenceGroupeService {

  private readonly api =
    'http://localhost:8081/api/preferences-groupes';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();

    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  afficherPreferences(professeurId: number): Observable<PreferenceGroupe[]> {
    return this.http.get<PreferenceGroupe[]>(
      `${this.api}/professeur/${professeurId}`,
      { headers: this.getHeaders() }
    );
  }

  ajouterPreference(
    professeurId: number,
    groupeId: number,
    statut: StatutPreference
  ): Observable<unknown> {

    const params = new HttpParams()
      .set('professeurId', professeurId)
      .set('groupeId', groupeId)
      .set('statut', statut);

    return this.http.post(
      this.api,
      null,
      {
        headers: this.getHeaders(),
        params
      }
    );
  }

  supprimerPreference(
    professeurId: number,
    groupeId: number
  ): Observable<void> {

    const params = new HttpParams()
      .set('professeurId', professeurId)
      .set('groupeId', groupeId);

    return this.http.delete<void>(
      this.api,
      {
        headers: this.getHeaders(),
        params
      }
    );
  }
}
