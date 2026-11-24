
import { Injectable } from '@angular/core';
import {
  HttpClient,
  HttpParams,
  HttpHeaders
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

// Nouvelle structure retournée par le DTO backend
export interface PreferenceMatiere {
  id: number;
  professeurId: number;
  matiereId: number;
  matiereNom: string;
}

export interface Matiere {
  id: number;
  nom: string;
  departementId: number | null;
  departementNom: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class PreferenceMatiereService {

  private readonly apiUrl =
    'https://edttt-backend-api.onrender.com/api/preferences-matieres';

  private readonly matiereUrl =
    'https://edttt-backend-api.onrender.com/api/matieres';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  /**
   * Ajouter le token JWT dans les headers HTTP.
   */
  private getHeaders(): HttpHeaders {
    const token = this.authService.getToken();

    return new HttpHeaders({
      Authorization: token?.startsWith('Bearer ')
        ? token
        : `Bearer ${token}`
    });
  }

  /**
   * Récupérer toutes les matières.
   */
  getMatieres(): Observable<Matiere[]> {
    return this.http.get<Matiere[]>(
      this.matiereUrl,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Ajouter une préférence pour un professeur.
   */
  ajouterPreference(
    professeurId: number,
    matiereId: number
  ): Observable<PreferenceMatiere> {

    const params = new HttpParams()
      .set('professeurId', professeurId)
      .set('matiereId', matiereId);

    return this.http.post<PreferenceMatiere>(
      this.apiUrl,
      null,
      {
        params,
        headers: this.getHeaders()
      }
    );
  }

  /**
   * Récupérer les préférences d'un professeur.
   */
  getByProfesseur(
    professeurId: number
  ): Observable<PreferenceMatiere[]> {

    return this.http.get<PreferenceMatiere[]>(
      `${this.apiUrl}/professeur/${professeurId}`,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Récupérer les professeurs ayant choisi une matière.
   */
  getByMatiere(
    matiereId: number
  ): Observable<PreferenceMatiere[]> {

    return this.http.get<PreferenceMatiere[]>(
      `${this.apiUrl}/matiere/${matiereId}`,
      { headers: this.getHeaders() }
    );
  }

  /**
   * Supprimer une préférence.
   */
  supprimerPreference(
    professeurId: number,
    matiereId: number
  ): Observable<void> {

    const params = new HttpParams()
      .set('professeurId', professeurId)
      .set('matiereId', matiereId);

    return this.http.delete<void>(
      this.apiUrl,
      {
        params,
        headers: this.getHeaders()
      }
    );
  }
}
