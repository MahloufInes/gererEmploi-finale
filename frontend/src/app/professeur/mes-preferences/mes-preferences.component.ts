
import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';

import {
  PreferenceMatiereService,
  Matiere,
  PreferenceMatiere
} from '../../services/preference-matiere.service';

interface UtilisateurConnecte {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: string;
}

@Component({
  selector: 'app-mes-preferences',
  templateUrl: './mes-preferences.component.html',
  styleUrls: ['./mes-preferences.component.css']
})
export class MesPreferencesComponent implements OnInit {

  matieres: Matiere[] = [];

  matieresSelectionnees: number[] = [];
  preferencesInitiales: number[] = [];

  messageSucces = '';
  messageErreur = '';

  chargement = false;
  enregistrementEnCours = false;

  recherche = '';

  private professeurId!: number;

  constructor(
    private preferenceService: PreferenceMatiereService
  ) {}

  ngOnInit(): void {
    this.chargerDonnees();
  }

  private obtenirUtilisateur(): UtilisateurConnecte | null {
    const utilisateurJSON = localStorage.getItem('user');

    if (!utilisateurJSON) {
      return null;
    }

    try {
      return JSON.parse(utilisateurJSON) as UtilisateurConnecte;
    } catch {
      return null;
    }
  }

  chargerDonnees(): void {

    const utilisateur = this.obtenirUtilisateur();

    this.messageErreur = '';
    this.messageSucces = '';

    if (
      !utilisateur ||
      !utilisateur.id ||
      utilisateur.role !== 'PROFESSEUR'
    ) {
      this.messageErreur =
        'Impossible de récupérer le professeur connecté.';
      return;
    }

    this.professeurId = utilisateur.id;
    this.chargement = true;

    forkJoin({
      matieres: this.preferenceService.getMatieres(),

      preferences: this.preferenceService.getByProfesseur(
        this.professeurId
      )
    }).subscribe({

      next: ({ matieres, preferences }) => {

        this.matieres = matieres;

        // Le backend retourne maintenant matiereId directement
        const ids = preferences
          .map((preference: PreferenceMatiere) =>
            preference.matiereId
          )
          .filter(
            (id): id is number =>
              id !== undefined && id !== null
          );

        this.matieresSelectionnees = [...new Set(ids)];

        this.preferencesInitiales = [
          ...this.matieresSelectionnees
        ];

        this.chargement = false;

      },

      error: (error) => {

        this.chargement = false;

        console.error('Erreur complète :', error);
        console.log('URL :', error.url);
        console.log('Statut HTTP :', error.status);
        console.log('Message :', error.message);
        console.log('Réponse backend :', error.error);

        if (error.status === 0) {
          this.messageErreur =
            'Impossible de contacter le serveur.';
        } else if (error.status === 401) {
          this.messageErreur =
            'Token JWT invalide ou expiré.';
        } else if (error.status === 403) {
          this.messageErreur =
            'Vous n’avez pas les autorisations nécessaires.';
        } else {
          this.messageErreur =
            error.error?.message ||
            'Erreur lors du chargement des matières et préférences.';
        }
      }
    });
  }

  get matieresFiltrees(): Matiere[] {

    const recherche = this.recherche.trim().toLowerCase();

    if (!recherche) {
      return this.matieres;
    }

    return this.matieres.filter(matiere =>
      matiere.nom.toLowerCase().includes(recherche) ||
      (matiere.departementNom || '')
        .toLowerCase()
        .includes(recherche)
    );
  }

  estSelectionnee(matiereId: number): boolean {
    return this.matieresSelectionnees.includes(matiereId);
  }

  toggleMatiere(matiereId: number): void {

    if (this.enregistrementEnCours || this.chargement) {
      return;
    }

    if (this.estSelectionnee(matiereId)) {

      this.matieresSelectionnees =
        this.matieresSelectionnees.filter(
          id => id !== matiereId
        );

    } else {

      this.matieresSelectionnees = [
        ...this.matieresSelectionnees,
        matiereId
      ];
    }

    this.messageErreur = '';
    this.messageSucces = '';
  }

  get nombreSelectionnees(): number {
    return this.matieresSelectionnees.length;
  }

  get nombreAjouts(): number {
    return this.matieresSelectionnees.filter(
      id => !this.preferencesInitiales.includes(id)
    ).length;
  }

  get nombreSuppressions(): number {
    return this.preferencesInitiales.filter(
      id => !this.matieresSelectionnees.includes(id)
    ).length;
  }

  enregistrer(): void {

    this.messageErreur = '';
    this.messageSucces = '';

    if (!this.professeurId) {
      this.messageErreur =
        'Session invalide. Veuillez vous reconnecter.';
      return;
    }

    const ajouts = this.matieresSelectionnees.filter(
      id => !this.preferencesInitiales.includes(id)
    );

    const suppressions = this.preferencesInitiales.filter(
      id => !this.matieresSelectionnees.includes(id)
    );

    if (ajouts.length === 0 && suppressions.length === 0) {
      this.messageSucces =
        'Aucune modification à enregistrer.';
      return;
    }

    const operations = [

      ...ajouts.map(id =>
        this.preferenceService.ajouterPreference(
          this.professeurId,
          id
        )
      ),

      ...suppressions.map(id =>
        this.preferenceService.supprimerPreference(
          this.professeurId,
          id
        )
      )

    ];

    this.enregistrementEnCours = true;

    forkJoin(operations).subscribe({

      next: () => {

        this.preferencesInitiales = [
          ...this.matieresSelectionnees
        ];

        this.enregistrementEnCours = false;

        this.messageSucces =
          'Vos préférences de matières ont été enregistrées avec succès !';
      },

      error: (error) => {

        console.error(
          'Erreur lors de l’enregistrement :',
          error
        );

        this.enregistrementEnCours = false;

        this.messageErreur =
          error.error?.message ||
          'Une erreur est survenue pendant l’enregistrement.';

        this.chargerDonnees();
      }
    });
  }

  reinitialiser(): void {

    if (this.enregistrementEnCours || this.chargement) {
      return;
    }

    this.matieresSelectionnees = [
      ...this.preferencesInitiales
    ];

    this.messageErreur = '';
    this.messageSucces = '';
  }
}
