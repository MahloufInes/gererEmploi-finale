
import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface Professeur {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  specialite: string;
  departementId: number;
  nomDepartement: string;
}

@Component({
  selector: 'app-dashboard-professeur',
  templateUrl: './dashboard-professeur.component.html',
  styleUrls: ['./dashboard-professeur.component.css']
})
export class DashboardProfesseurComponent implements OnInit {

  professeur: Professeur | null = null;

  chargement = true;
  messageErreur = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.chargerMonProfil();
  }

  chargerMonProfil(): void {

    const token = localStorage.getItem('token');

    if (!token) {
      this.messageErreur = 'Vous devez vous connecter pour accéder à votre espace.';
      this.chargement = false;
      return;
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`
    });

    this.http.get<Professeur>(
      'http://localhost:8081/api/professeurs/mon-profil',
      { headers }
    ).subscribe({

      next: (data) => {
        this.professeur = data;
        this.chargement = false;
      },

      error: (err) => {
        console.error('Erreur chargement profil professeur :', err);

        this.messageErreur =
          'Impossible de charger vos informations professionnelles.';

        this.chargement = false;
      }

    });
  }
}
