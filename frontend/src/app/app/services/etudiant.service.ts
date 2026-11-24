
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EtudiantService {

  private apiUrl = 'https://edttt-backend-api.onrender.com/api/etudiants';

  constructor(private http: HttpClient) {}

  afficherTous(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }
}
