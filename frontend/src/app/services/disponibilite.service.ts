
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

  private apiUrl = 'http://localhost:8081/api/disponibilites';

  constructor(private http: HttpClient) {}

  enregistrerDisponibilites(
    professeurId: number,
    request: DisponibiliteRequest,
    token: string
  ): Observable<DisponibiliteResponse> {

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    return this.http.post<DisponibiliteResponse>(
      `${this.apiUrl}/professeur/${professeurId}`,
      request,
      { headers }
    );
  }
}
