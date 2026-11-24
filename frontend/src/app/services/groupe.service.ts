
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Groupe {
  id: number;
  nom: string;
  effectif: number;
  filiereId: number;
  filiere: string;
  semestres: string[];
}

@Injectable({
  providedIn: 'root'
})
export class GroupeService {

  private readonly api = 'http://localhost:8081/api/groupes';

  constructor(private http: HttpClient) {}

  afficherTous(): Observable<Groupe[]> {
    return this.http.get<Groupe[]>(this.api);
  }
}
