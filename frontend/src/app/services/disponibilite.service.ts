
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CreneauSelectionne {
  jour: string;
  heureDebut: string;
  heureFin: string;
}

export interface DisponibiliteRequest {
  creneaux: CreneauSelectionne[];
}

export interface DisponibiliteResponse {
  message: string;
  nombreAjoute: number;
}

@Injectable({
  providedIn: 'root'
})
export class DisponibiliteService {

  private apiUrl = 'https://gereremploi-finale-4h75.onrender.com/api/disponibilites';

  constructor(private http: HttpClient) {}

  private getHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  // Enregistrer les disponibilités
  enregistrerDisponibilites(
    professeurId: number,
    request: DisponibiliteRequest,
    token: string
  ): Observable<DisponibiliteResponse> {

    return this.http.post<DisponibiliteResponse>(
      `${this.apiUrl}/professeur/${professeurId}`,
      request,
      { headers: this.getHeaders(token) }
    );
  }

  // Récupérer les disponibilités déjà enregistrées
  getDisponibilites(
    professeurId: number,
    token: string
  ): Observable<CreneauSelectionne[]> {

    return this.http.get<CreneauSelectionne[]>(
      `${this.apiUrl}/professeur/${professeurId}`,
      { headers: this.getHeaders(token) }
    );
  }
}
