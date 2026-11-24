
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { Groupe, GroupeService } from '../../services/groupe.service';

import {
  PreferenceGroupe,
  PreferenceGroupeService,
  StatutPreference
} from '../../services/preference-groupe.service';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-preferences-groupes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule
  ],
  templateUrl: './preferences-groupes.component.html',
  styleUrls: ['./preferences-groupes.component.css']
})
export class PreferencesGroupesComponent implements OnInit {

  groupes: Groupe[] = [];
  preferences: PreferenceGroupe[] = [];

  professeurId: number | null = null;

  recherche: string = '';
  semestreSelectionne: string = '';

  semestresDisponibles: string[] = [
    'S1', 'S2', 'S3', 'S4', 'S5'
  ];

  chargement: boolean = false;
  groupeEnCours: number | null = null;

  messageSucces: string = '';
  messageErreur: string = '';

  constructor(
    private groupeService: GroupeService,
    private preferenceService: PreferenceGroupeService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.professeurId = this.authService.getUserId();

    if (this.professeurId === null) {
      this.messageErreur = 'Utilisateur non connecté.';
      return;
    }

    this.chargerDonnees();
  }

  // Charger les groupes et les préférences du professeur
  chargerDonnees(): void {
    if (this.professeurId === null) {
      return;
    }

    this.chargement = true;
    this.messageErreur = '';

    forkJoin({
      groupes: this.groupeService.afficherTous(),
      preferences: this.preferenceService.afficherPreferences(
        this.professeurId
      )
    }).subscribe({
      next: (resultat) => {
        this.groupes = resultat.groupes || [];
        this.preferences = resultat.preferences || [];
        this.chargement = false;
      },
      error: (err) => {
        console.error('Erreur de chargement :', err);

        this.messageErreur =
          err.error?.message ||
          'Impossible de charger les groupes et les préférences.';

        this.chargement = false;
      }
    });
  }

  // Filtrer les groupes par nom, filière et semestre
  get groupesFiltres(): Groupe[] {
    return this.groupes.filter(groupe => {
      const recherche = this.recherche.toLowerCase().trim();

      const nomGroupe = (groupe.nom || '').toLowerCase();
      const nomFiliere = (groupe.filiere || '').toLowerCase();

      const correspondRecherche =
        nomGroupe.includes(recherche) ||
        nomFiliere.includes(recherche);

      const correspondSemestre =
        !this.semestreSelectionne ||
        (groupe.semestres || []).includes(this.semestreSelectionne);

      return correspondRecherche && correspondSemestre;
    });
  }

  // Récupérer la préférence d'un groupe
  getPreference(groupeId: number): StatutPreference | null {
    const preference = this.preferences.find(
      p => p.groupeId === groupeId
    );

    return preference ? preference.statut : null;
  }

  // Ajouter ou modifier une préférence
  definirPreference(
    groupeId: number,
    statut: StatutPreference
  ): void {

    if (this.professeurId === null || this.groupeEnCours !== null) {
      return;
    }

    this.groupeEnCours = groupeId;
    this.messageSucces = '';
    this.messageErreur = '';

    const anciennePreference = this.preferences.find(
      p => p.groupeId === groupeId
    );

    // Si une préférence existe, on la supprime avant d'enregistrer
    // le nouveau statut.
    const operation = anciennePreference
      ? this.preferenceService.supprimerPreference(
          this.professeurId,
          groupeId
        ).pipe(
          switchMap(() =>
            this.preferenceService.ajouterPreference(
              this.professeurId!,
              groupeId,
              statut
            )
          )
        )
      : this.preferenceService.ajouterPreference(
          this.professeurId,
          groupeId,
          statut
        );

    // Après l'enregistrement, récupérer les données du backend
    operation.pipe(
      switchMap(() =>
        this.preferenceService.afficherPreferences(this.professeurId!)
      )
    ).subscribe({
      next: (preferencesActualisees) => {
        this.preferences = preferencesActualisees || [];

        this.messageSucces =
          statut === 'REFUSE'
            ? 'Groupe refusé avec succès.'
            : 'Groupe accepté avec succès.';

        this.groupeEnCours = null;
      },
      error: (err) => {
        console.error('Erreur lors de la modification :', err);

        this.messageErreur =
          err.error?.message ||
          'Impossible d’enregistrer cette préférence. Vérifiez le backend.';

        this.groupeEnCours = null;
      }
    });
  }

  // Supprimer une préférence
  supprimerPreference(groupeId: number): void {

    if (this.professeurId === null || this.groupeEnCours !== null) {
      return;
    }

    this.groupeEnCours = groupeId;
    this.messageSucces = '';
    this.messageErreur = '';

    this.preferenceService.supprimerPreference(
      this.professeurId,
      groupeId
    ).pipe(
      switchMap(() =>
        this.preferenceService.afficherPreferences(this.professeurId!)
      )
    ).subscribe({
      next: (preferencesActualisees) => {
        this.preferences = preferencesActualisees || [];

        this.messageSucces = 'Préférence supprimée avec succès.';
        this.groupeEnCours = null;
      },
      error: (err) => {
        console.error('Erreur suppression :', err);

        this.messageErreur =
          err.error?.message ||
          'Impossible de supprimer cette préférence.';

        this.groupeEnCours = null;
      }
    });
  }
}
