
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface LoginResponse {
  token: string;
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: 'ADMINISTRATEUR' | 'SOUS_ADMINISTRATEUR' | 'PROFESSEUR' | 'ETUDIANT';
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = 'https://edttt-backend-api.onrender.com/api/auth';

  constructor(private http: HttpClient) {}

  login(email: string, motDePasse: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      { email, motDePasse }
    ).pipe(
      tap(response => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('role', response.role);
        localStorage.setItem('user', JSON.stringify(response));
      })
    );
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getCurrentUser(): LoginResponse | null {
    const user = localStorage.getItem('user');

    if (!user) {
      return null;
    }

    try {
      return JSON.parse(user) as LoginResponse;
    } catch {
      return null;
    }
  }

  getUserId(): number | null {
    return this.getCurrentUser()?.id ?? null;
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    localStorage.removeItem('user');
  }
}
