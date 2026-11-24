
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
  selector: 'app-emploi-professeur',
  templateUrl: './emploi-professeur-component.component.html',
  styleUrls: ['./emploi-professeur-component.component.css']
})
export class EmploiProfesseurComponent implements OnInit {

  private readonly api =
    'https://edttt-backend-api.onrender.com/api/seances/mon-prof-emploi';

  private readonly ordreJours = [
    'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI',
    'VENDREDI', 'SAMEDI', 'DIMANCHE'
  ];

  seances: Seance[] = [];
  chargement = false;
  messageErreur = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.chargerEmploiDuTemps();
  }

  chargerEmploiDuTemps(): void {
    const token = localStorage.getItem('token');

    if (!token) {
      this.messageErreur = 'Utilisateur non connecté.';
      return;
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`
    });

    this.chargement = true;
    this.messageErreur = '';

    this.http.get<Seance[]>(this.api, { headers }).subscribe({
      next: (data) => {
        this.seances = data.sort((a, b) =>
          this.ordreJours.indexOf(a.jour) -
          this.ordreJours.indexOf(b.jour) ||
          a.heureDebut.localeCompare(b.heureDebut)
        );

        this.chargement = false;
      },
      error: (err) => {
        console.error('Erreur emploi du temps :', err);

        if (err.status === 403) {
          this.messageErreur = 'Accès refusé (compte non professeur).';
        } else if (err.status === 401) {
          this.messageErreur = 'Session expirée, reconnectez-vous.';
        } else {
          this.messageErreur =
            'Impossible de charger votre emploi du temps.';
        }

        this.chargement = false;
      }
    });
  }
}
