
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Departement {
  id: number;
  code: string;
  nom: string;
}

@Injectable({
  providedIn: 'root'
})
export class DepartementService {

  private apiUrl = 'https://edttt-backend-api.onrender.com/api/departements';

  constructor(private http: HttpClient) {}

  afficherTous(): Observable<Departement[]> {
    return this.http.get<Departement[]>(this.apiUrl);
  }
}
