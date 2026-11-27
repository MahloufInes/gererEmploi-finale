
import { Component, OnInit } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import Swal from 'sweetalert2';

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
  PreferenceMatiere,
  PreferenceMatiereService
} from '../../services/preference-matiere.service';

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

  // Matières préférées de chaque professeur pour le tableau
  preferencesParProfesseur: { [id: number]: string[] } = {};

  matiereSelectionnee = 0;
  chargementPreferences = false;
  enregistrementPreference = false;

  constructor(
    private professeurService: ProfesseurService,
    private departementService: DepartementService,
    private preferenceMatiereService: PreferenceMatiereService
  ) {}

  ngOnInit(): void {
    this.chargerProfesseurs();
    this.chargerDepartements();
  }

  // ==========================================
  // CHARGEMENT DES PROFESSEURS
  // ==========================================

  chargerProfesseurs(): void {
    this.chargement = true;
    this.messageErreur = '';

    this.professeurService.afficherTous().subscribe({
      next: (data) => {
        this.professeurs = data;
        this.chargement = false;

        // Charger les matières préférées de chaque professeur
        this.chargerPreferencesTableau();
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

  // ==========================================
  // MATIERES PREFEREES DANS LE TABLEAU
  // ==========================================

  chargerPreferencesTableau(): void {

    this.preferencesParProfesseur = {};

    if (this.professeurs.length === 0) {
      return;
    }

    const requetes = this.professeurs.map(professeur =>
      this.preferenceMatiereService
        .getByProfesseur(professeur.id)
        .pipe(
          catchError(error => {
            console.error(
              `Erreur préférences professeur ${professeur.id}`,
              error
            );

            return of([]);
          })
        )
    );

    forkJoin(requetes).subscribe({
      next: (resultats) => {

        this.professeurs.forEach((professeur, index) => {
          this.preferencesParProfesseur[professeur.id] =
            resultats[index].map(preference => preference.matiereNom);
        });

      },
      error: (error) => {
        console.error(
          'Erreur chargement des matières préférées',
          error
        );
      }
    });
  }

  matieresPreferees(professeurId: number): string {
    const matieres = this.preferencesParProfesseur[professeurId];

    if (!matieres || matieres.length === 0) {
      return 'Aucune matière';
    }

    return matieres.join(', ');
  }

  // ==========================================
  // FORMULAIRE AJOUT PROFESSEUR
  // ==========================================

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

    // Vérification des champs obligatoires
    if (
      !p.nom.trim() ||
      !p.prenom.trim() ||
      !p.email.trim() ||
      !p.specialite.trim() ||
      Number(p.departementId) <= 0
    ) {
      Swal.fire({
        icon: 'warning',
        title: 'Champs obligatoires',
        text: 'Veuillez remplir tous les champs.',
        confirmButtonText: 'OK'
      });
      return;
    }

    // Vérification de l'adresse email
    const emailValide =
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(p.email.trim());

    if (!emailValide) {
      Swal.fire({
        icon: 'warning',
        title: 'Email invalide',
        text: 'Veuillez saisir une adresse email valide.',
        confirmButtonText: 'OK'
      });
      return;
    }

    // Vérification du mot de passe
    if (!p.motDePasse || p.motDePasse.length < 8) {
      Swal.fire({
        icon: 'warning',
        title: 'Mot de passe invalide',
        text: 'Le mot de passe doit contenir au moins 8 caractères.',
        confirmButtonText: 'OK'
      });
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
        this.enregistrement = false;
        this.afficherFormulaire = false;

        this.reinitialiserFormulaire();
        this.chargerProfesseurs();

        Swal.fire({
          icon: 'success',
          title: 'Enseignant ajouté !',
          text: 'L’enseignant a été ajouté avec succès.',
          confirmButtonText: 'Parfait',
          timer: 2500,
          timerProgressBar: true
        });
      },

      error: (error) => {
        console.error('Erreur ajout professeur', error);
        this.enregistrement = false;

        let message = '';

        if (error.status === 409) {
          message = 'Cette adresse email existe déjà.';
        } else if (error.status === 404) {
          message = 'Département introuvable.';
        } else {
          message = this.messageHttp(
            error,
            'Erreur lors de l’ajout de l’enseignant.'
          );
        }

        Swal.fire({
          icon: 'error',
          title: 'Échec de l’ajout',
          text: message,
          confirmButtonText: 'OK'
        });
      }
    });
  }

  // ==========================================
  // GESTION DES PREFERENCES
  // ==========================================

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
    this.preferenceMatiereService.getMatieres().subscribe({
      next: (data) => {
        this.matieres = data;
      },
      error: (error) => {
        console.error('Erreur chargement matières', error);

        Swal.fire({
          icon: 'error',
          title: 'Erreur',
          text: this.messageHttp(
            error,
            'Impossible de charger les matières.'
          ),
          confirmButtonText: 'OK'
        });
      }
    });
  }

  chargerPreferences(): void {

    if (!this.professeurSelectionne) {
      return;
    }

    this.chargementPreferences = true;

    const professeurId = this.professeurSelectionne.id;

    this.preferenceMatiereService
      .getByProfesseur(professeurId)
      .subscribe({
        next: (data) => {

          this.preferences = data;

          // Actualiser également le tableau
          this.preferencesParProfesseur[professeurId] =
            data.map(preference => preference.matiereNom);

          this.chargementPreferences = false;
        },

        error: (error) => {
          console.error('Erreur chargement préférences', error);

          this.chargementPreferences = false;

          Swal.fire({
            icon: 'error',
            title: 'Erreur de chargement',
            text: this.messageHttp(
              error,
              'Impossible de charger les matières préférées.'
            ),
            confirmButtonText: 'OK'
          });
        }
      });
  }

  estMatiereSelectionnee(matiereId: number): boolean {
    return this.preferences.some(
      preference => preference.matiereId === matiereId
    );
  }

  // ==========================================
  // AJOUTER UNE MATIERE PREFEREE
  // ==========================================

  ajouterPreference(): void {

    if (
      !this.professeurSelectionne ||
      Number(this.matiereSelectionnee) <= 0
    ) {
      Swal.fire({
        icon: 'warning',
        title: 'Attention',
        text: 'Veuillez choisir une matière.',
        confirmButtonText: 'OK'
      });

      return;
    }

    if (
      this.estMatiereSelectionnee(
        Number(this.matiereSelectionnee)
      )
    ) {
      Swal.fire({
        icon: 'info',
        title: 'Matière déjà sélectionnée',
        text: 'Cette matière figure déjà dans les préférences.',
        confirmButtonText: 'OK'
      });

      return;
    }

    this.enregistrementPreference = true;

    const professeurId = this.professeurSelectionne.id;
    const matiereId = Number(this.matiereSelectionnee);

    this.preferenceMatiereService
      .ajouterPreference(professeurId, matiereId)
      .subscribe({

        next: () => {

          this.matiereSelectionnee = 0;
          this.enregistrementPreference = false;

          this.chargerPreferences();
          this.chargerPreferencesTableau();

          Swal.fire({
            icon: 'success',
            title: 'Matière ajoutée !',
            text: 'La matière a été ajoutée aux préférences du professeur.',
            confirmButtonText: 'Parfait',
            timer: 2500,
            timerProgressBar: true
          });
        },

        error: (error) => {
          console.error('Erreur ajout préférence', error);

          this.enregistrementPreference = false;

          Swal.fire({
            icon: 'error',
            title: 'Échec de l’ajout',
            text: this.messageHttp(
              error,
              'Erreur lors de l’ajout de la préférence.'
            ),
            confirmButtonText: 'OK'
          });
        }
      });
  }

  // ==========================================
  // SUPPRIMER UNE MATIERE PREFEREE
  // ==========================================

  supprimerPreference(matiereId: number): void {

    if (!this.professeurSelectionne) {
      return;
    }

    const professeurId = this.professeurSelectionne.id;

    const preference = this.preferences.find(
      pref => pref.matiereId === matiereId
    );

    const nomMatiere = preference?.matiereNom || 'cette matière';

    Swal.fire({
      icon: 'warning',
      title: 'Supprimer cette préférence ?',
      text: `Voulez-vous vraiment supprimer ${nomMatiere} ?`,
      showCancelButton: true,
      confirmButtonText: 'Oui, supprimer',
      cancelButtonText: 'Annuler',
      reverseButtons: true
    }).then((result) => {

      if (!result.isConfirmed) {
        return;
      }

      this.preferenceMatiereService
        .supprimerPreference(professeurId, matiereId)
        .subscribe({

          next: () => {

            this.chargerPreferences();
            this.chargerPreferencesTableau();

            Swal.fire({
              icon: 'success',
              title: 'Préférence supprimée !',
              text: 'La matière a été supprimée des préférences.',
              confirmButtonText: 'OK',
              timer: 2500,
              timerProgressBar: true
            });
          },

          error: (error) => {
            console.error('Erreur suppression préférence', error);

            Swal.fire({
              icon: 'error',
              title: 'Erreur de suppression',
              text: this.messageHttp(
                error,
                'Erreur lors de la suppression de la préférence.'
              ),
              confirmButtonText: 'OK'
            });
          }
        });
    });
  }

  get matieresDisponibles(): Matiere[] {
    return this.matieres.filter(
      matiere => !this.estMatiereSelectionnee(matiere.id)
    );
  }

  // ==========================================
  // GESTION DES ERREURS HTTP
  // ==========================================

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
