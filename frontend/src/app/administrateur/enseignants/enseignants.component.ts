
import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

import {
  Professeur,
  ProfesseurService,
  ProfesseurRequest
} from '../../services/professeur.service';

import {
  Departement,
  DepartementService
} from '../../services/departement.service';

import {
  Matiere,
  MatiereService
} from '../../services/matiere.service';

interface PreferenceMatiere {
  id: number;
  professeur: {
    id: number;
  };
  matiere: {
    id: number;
    nom: string;
  };
}

@Component({
  selector: 'app-enseignants',
  templateUrl: './enseignants.component.html',
  styleUrls: ['./enseignants.component.css']
})
export class EnseignantsComponent implements OnInit {

  professeurs: Professeur[] = [];
  departements: Departement[] = [];

  chargement = false;
  messageErreur = '';
  messageSucces = '';

  afficherFormulaire = false;
  enregistrement = false;
  afficherMotDePasse = false;

  nouveauProfesseur: ProfesseurRequest = {
    nom: '',
    prenom: '',
    email: '',
    motDePasse: '',
    specialite: '',
    departementId: 0
  };

  // Gestion des matières préférées
  afficherPreferences = false;
  professeurSelectionne: Professeur | null = null;

  matieres: Matiere[] = [];
  preferences: PreferenceMatiere[] = [];

  matiereSelectionnee = 0;
  chargementPreferences = false;
  enregistrementPreference = false;

  private apiPreferences =
    'http://localhost:8081/api/preferences-matieres';

  constructor(
    private professeurService: ProfesseurService,
    private departementService: DepartementService,
    private matiereService: MatiereService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.chargerProfesseurs();
    this.chargerDepartements();
  }

  chargerProfesseurs(): void {
    this.chargement = true;
    this.messageErreur = '';

    this.professeurService.afficherTous().subscribe({
      next: (data) => {
        this.professeurs = data;
        this.chargement = false;
      },
      error: (error) => {
        console.error('Erreur de chargement', error);
        this.messageErreur = this.messageHttp(
          error,
          'Impossible de charger les professeurs.'
        );
        this.chargement = false;
      }
    });
  }

  chargerDepartements(): void {
    this.departementService.afficherTous().subscribe({
      next: (data) => {
        this.departements = data;
      },
      error: (error) => {
        console.error('Erreur départements', error);
        this.messageErreur = this.messageHttp(
          error,
          'Impossible de charger les départements.'
        );
      }
    });
  }

  ouvrirFormulaire(): void {
    this.reinitialiserFormulaire();
    this.afficherFormulaire = true;
    this.messageErreur = '';
    this.messageSucces = '';
  }

  annuler(): void {
    this.afficherFormulaire = false;
    this.reinitialiserFormulaire();
  }

  reinitialiserFormulaire(): void {
    this.nouveauProfesseur = {
      nom: '',
      prenom: '',
      email: '',
      motDePasse: '',
      specialite: '',
      departementId: 0
    };

    this.afficherMotDePasse = false;
  }

  ajouterProfesseur(): void {
    this.messageErreur = '';
    this.messageSucces = '';

    const p = this.nouveauProfesseur;

    if (
      !p.nom.trim() ||
      !p.prenom.trim() ||
      !p.email.trim() ||
      !p.specialite.trim() ||
      Number(p.departementId) <= 0
    ) {
      this.messageErreur = 'Veuillez remplir tous les champs.';
      return;
    }

    const emailValide =
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(p.email.trim());

    if (!emailValide) {
      this.messageErreur =
        'Veuillez saisir une adresse email valide.';
      return;
    }

    if (!p.motDePasse || p.motDePasse.length < 8) {
      this.messageErreur =
        'Le mot de passe doit contenir au moins 8 caractères.';
      return;
    }

    this.enregistrement = true;

    const request: ProfesseurRequest = {
      nom: p.nom.trim(),
      prenom: p.prenom.trim(),
      email: p.email.trim().toLowerCase(),
      motDePasse: p.motDePasse,
      specialite: p.specialite.trim(),
      departementId: Number(p.departementId)
    };

    this.professeurService.ajouter(request).subscribe({
      next: () => {
        this.messageSucces =
          'Enseignant ajouté avec succès !';

        this.enregistrement = false;
        this.afficherFormulaire = false;

        this.reinitialiserFormulaire();
        this.chargerProfesseurs();
      },

      error: (error) => {
        console.error('Erreur ajout professeur', error);
        this.enregistrement = false;

        if (error.status === 409) {
          this.messageErreur =
            'Cette adresse email existe déjà.';
        } else if (error.status === 404) {
          this.messageErreur = 'Département introuvable.';
        } else {
          this.messageErreur = this.messageHttp(
            error,
            'Erreur lors de l’ajout de l’enseignant.'
          );
        }
      }
    });
  }

  // Gestion des préférences

  ouvrirPreferences(professeur: Professeur): void {
    this.professeurSelectionne = professeur;
    this.afficherPreferences = true;

    this.messageErreur = '';
    this.messageSucces = '';

    this.matiereSelectionnee = 0;

    this.chargerMatieres();
    this.chargerPreferences();
  }

  fermerPreferences(): void {
    this.afficherPreferences = false;
    this.professeurSelectionne = null;
    this.preferences = [];
    this.matiereSelectionnee = 0;
  }

  chargerMatieres(): void {
    this.matiereService.getAll().subscribe({
      next: (data) => {
        this.matieres = data;
      },
      error: (error) => {
        console.error('Erreur chargement matières', error);
        this.messageErreur = this.messageHttp(
          error,
          'Impossible de charger les matières.'
        );
      }
    });
  }

  chargerPreferences(): void {
    if (!this.professeurSelectionne) {
      return;
    }

    this.chargementPreferences = true;

    const professeurId = this.professeurSelectionne.id;

    this.http.get<PreferenceMatiere[]>(
      `${this.apiPreferences}/professeur/${professeurId}`
    ).subscribe({
      next: (data) => {
        this.preferences = data;
        this.chargementPreferences = false;
      },
      error: (error) => {
        console.error('Erreur chargement préférences', error);
        this.messageErreur = this.messageHttp(
          error,
          'Impossible de charger les matières préférées.'
        );
        this.chargementPreferences = false;
      }
    });
  }

  estMatiereSelectionnee(matiereId: number): boolean {
    return this.preferences.some(
      preference => preference.matiere.id === matiereId
    );
  }

  ajouterPreference(): void {
    if (
      !this.professeurSelectionne ||
      Number(this.matiereSelectionnee) <= 0
    ) {
      this.messageErreur = 'Veuillez choisir une matière.';
      return;
    }

    if (this.estMatiereSelectionnee(
      Number(this.matiereSelectionnee)
    )) {
      this.messageErreur =
        'Cette matière est déjà sélectionnée.';
      return;
    }

    this.enregistrementPreference = true;
    this.messageErreur = '';

    const params = new HttpParams()
      .set('professeurId', this.professeurSelectionne.id)
      .set('matiereId', Number(this.matiereSelectionnee));

    this.http.post<PreferenceMatiere>(
      this.apiPreferences,
      null,
      { params }
    ).subscribe({
      next: () => {
        this.messageSucces =
          'Matière ajoutée aux préférences !';

        this.matiereSelectionnee = 0;
        this.enregistrementPreference = false;
        this.chargerPreferences();
      },
      error: (error) => {
        console.error('Erreur ajout préférence', error);
        this.messageErreur = this.messageHttp(
          error,
          'Erreur lors de l’ajout de la préférence.'
        );
        this.enregistrementPreference = false;
      }
    });
  }

  supprimerPreference(matiereId: number): void {
    if (!this.professeurSelectionne) {
      return;
    }

    const params = new HttpParams()
      .set('professeurId', this.professeurSelectionne.id)
      .set('matiereId', matiereId);

    this.http.delete<void>(
      this.apiPreferences,
      { params }
    ).subscribe({
      next: () => {
        this.messageSucces =
          'Préférence supprimée avec succès !';

        this.chargerPreferences();
      },
      error: (error) => {
        console.error('Erreur suppression préférence', error);
        this.messageErreur = this.messageHttp(
          error,
          'Erreur lors de la suppression.'
        );
      }
    });
  }

  get matieresDisponibles(): Matiere[] {
    return this.matieres.filter(
      matiere => !this.estMatiereSelectionnee(matiere.id)
    );
  }

  private messageHttp(error: any, defaut: string): string {
    if (error.status === 401) {
      return 'Session expirée. Reconnectez-vous.';
    }

    if (error.status === 403) {
      return 'Accès refusé : réservé aux administrateurs.';
    }

    if (error.status === 0) {
      return 'Serveur injoignable.';
    }

    return error.error?.message || defaut;
  }
}
