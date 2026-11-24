
import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {

  nombreEtudiants = 0;
  nombreFilieres = 0;
  nombreGroupes = 0;
  nombreEnseignants = 0;

  private apiEtudiants = 'https://edttt-backend-api.onrender.com/api/etudiants';
  private apiFilieres = 'https://edttt-backend-api.onrender.com/api/filieres';
  private apiGroupes = 'https://edttt-backend-api.onrender.com/api/groupes';
  private apiEnseignants = 'https://edttt-backend-api.onrender.com/api/professeurs/count';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.chargerNombreEtudiants();
    this.chargerNombreFilieres();
    this.chargerNombreGroupes();
    this.chargerNombreEnseignants();
  }

  chargerNombreEtudiants(): void {
    this.http.get<any[]>(this.apiEtudiants).subscribe({
      next: data => this.nombreEtudiants = data.length,
      error: error => console.error('Erreur étudiants', error)
    });
  }

  chargerNombreFilieres(): void {
    this.http.get<any[]>(this.apiFilieres).subscribe({
      next: data => this.nombreFilieres = data.length,
      error: error => console.error('Erreur filières', error)
    });
  }

  chargerNombreGroupes(): void {
    this.http.get<any[]>(this.apiGroupes).subscribe({
      next: data => this.nombreGroupes = data.length,
      error: error => console.error('Erreur groupes', error)
    });
  }

  chargerNombreEnseignants(): void {
    this.http.get<number>(this.apiEnseignants).subscribe({
      next: data => this.nombreEnseignants = data,
      error: error => console.error('Erreur enseignants', error)
    });
  }
}
