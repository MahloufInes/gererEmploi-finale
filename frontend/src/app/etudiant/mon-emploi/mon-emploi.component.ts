import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface Seance {
   id: number;
  date: string;
  jour: string;
  heureDebut: string;
  heureFin: string;
  type: string;
  statut: string;
  matiereNom: string;
  professeurNom: string;
  salleNom: string;
  groupeNom: string;
}

@Component({
  selector: 'app-mon-emploi',
  templateUrl: './mon-emploi.component.html',
  styleUrls: ['./mon-emploi.component.css']
})
export class MonEmploiComponent implements OnInit {

  private readonly api = 'http://localhost:8081/api/consultation/mon-emploi';
  private readonly ordreJours = ['LUNDI','MARDI','MERCREDI','JEUDI','VENDREDI','SAMEDI','DIMANCHE'];

  seances: Seance[] = [];
  semestres: string[] = [];
  semestreChoisi = '';
  chargement = false;
  messageErreur = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.chargerSemestres();
  }

  private headers(): HttpHeaders | null {
    const token = localStorage.getItem('token');
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : null;
  }

  chargerSemestres(): void {
    const headers = this.headers();
    if (!headers) { this.messageErreur = 'Utilisateur non connecté.'; return; }

    this.chargement = true;
    this.http.get<string[]>(`${this.api}/semestres`, { headers }).subscribe({
      next: (data) => {
        this.semestres = data;
        if (data.length > 0) {
          this.semestreChoisi = data[0];
          this.chargerSeances();
        } else {
          this.chargement = false;
        }
      },
      error: (err) => this.gererErreur(err)
    });
  }

  chargerSeances(): void {
    const headers = this.headers();
    if (!headers || !this.semestreChoisi) return;

    this.chargement = true;
    this.messageErreur = '';

    this.http.get<Seance[]>(this.api, {
      headers,
      params: { semestre: this.semestreChoisi }
    }).subscribe({
      next: (data) => {
        this.seances = data.sort((a, b) =>
          this.ordreJours.indexOf(a.jour) - this.ordreJours.indexOf(b.jour)
          || a.heureDebut.localeCompare(b.heureDebut));
        this.chargement = false;
      },
      error: (err) => this.gererErreur(err)
    });
  }

  changerSemestre(event: Event): void {
    this.semestreChoisi = (event.target as HTMLSelectElement).value;
    this.chargerSeances();
  }

  private gererErreur(err: any): void {
    console.error('Erreur emploi du temps :', err);
    this.chargement = false;
    if (err.status === 403) this.messageErreur = 'Accès refusé (compte non étudiant).';
    else if (err.status === 404) this.messageErreur = err.error?.message || 'Aucun groupe associé à votre compte.';
    else if (err.status === 401) this.messageErreur = 'Session expirée, reconnectez-vous.';
    else this.messageErreur = 'Impossible de charger votre emploi du temps.';
  }
}