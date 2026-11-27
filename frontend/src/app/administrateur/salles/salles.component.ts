
import { Component, OnInit } from '@angular/core';

import {
  Salle,
  SalleService,
  SalleRequest
} from '../../services/salle.service';

import Swal from 'sweetalert2';

@Component({
  selector: 'app-salles',
  templateUrl: './salles.component.html',
  styleUrls: ['./salles.component.css']
})
export class SallesComponent implements OnInit {

  salles: Salle[] = [];

  chargement = false;
  enregistrement = false;
  messageErreur = '';

  afficherFormulaire = false;
  modeModification = false;
  salleIdModifie: number | null = null;

  nouvelleSalle: SalleRequest = {
    nom: '',
    capacite: 30,
    type: 'COURS'
  };

  constructor(private salleService: SalleService) {}

  ngOnInit(): void {
    this.chargerSalles();
  }

  // ==========================================
  // CHARGER LES SALLES
  // ==========================================

  chargerSalles(): void {
    this.chargement = true;
    this.messageErreur = '';

    this.salleService.afficherToutes().subscribe({
      next: data => {
        this.salles = data;
        this.chargement = false;
      },

      error: error => {
        console.error('Erreur de chargement', error);

        this.chargement = false;
        this.messageErreur = this.messageHttp(
          error,
          'Impossible de charger les salles.'
        );

        Swal.fire({
          icon: 'error',
          title: 'Erreur de chargement',
          text: this.messageErreur,
          confirmButtonText: 'OK',
          confirmButtonColor: '#5142a5'
        });
      }
    });
  }

  // ==========================================
  // OUVRIR LE FORMULAIRE D'AJOUT
  // ==========================================

  ouvrirFormulaire(): void {
    this.afficherFormulaire = true;
    this.modeModification = false;
    this.salleIdModifie = null;

    this.nouvelleSalle = {
      nom: '',
      capacite: 30,
      type: 'COURS'
    };
  }

  // ==========================================
  // FERMER LE FORMULAIRE
  // ==========================================

  fermerFormulaire(): void {
    this.afficherFormulaire = false;
    this.reinitialiserFormulaire();
  }

  reinitialiserFormulaire(): void {
    this.nouvelleSalle = {
      nom: '',
      capacite: 30,
      type: 'COURS'
    };

    this.modeModification = false;
    this.salleIdModifie = null;
  }

  // ==========================================
  // AJOUTER OU MODIFIER UNE SALLE
  // ==========================================

  enregistrerSalle(): void {

    const nom = this.nouvelleSalle.nom?.trim();
    const capacite = Number(this.nouvelleSalle.capacite);
    const type = this.nouvelleSalle.type;

    // Validation des champs
    if (!nom || !type || !Number.isFinite(capacite) || capacite <= 0) {

      Swal.fire({
        icon: 'warning',
        title: 'Champs invalides',
        text: 'Veuillez remplir correctement tous les champs. La capacité doit être supérieure à zéro.',
        confirmButtonText: 'OK',
        confirmButtonColor: '#5142a5'
      });

      return;
    }

    if (this.enregistrement) {
      return;
    }

    // Préparation des données
    const salleRequest: SalleRequest = {
      nom: nom,
      capacite: capacite,
      type: type
    };

    this.enregistrement = true;

    // Choisir entre ajout et modification
    const operation =
      this.modeModification && this.salleIdModifie !== null
        ? this.salleService.modifier(
            this.salleIdModifie,
            salleRequest
          )
        : this.salleService.ajouter(salleRequest);

    operation.subscribe({

      next: () => {

        this.enregistrement = false;

        const modification = this.modeModification;

        this.fermerFormulaire();

        Swal.fire({
          icon: 'success',
          title: modification
            ? 'Salle modifiée !'
            : 'Salle ajoutée !',

          text: modification
            ? 'Les modifications ont été enregistrées avec succès.'
            : 'La nouvelle salle a été ajoutée avec succès.',

          confirmButtonText: 'Parfait !',
          confirmButtonColor: '#5142a5',
          timer: 2500,
          timerProgressBar: true
        });

        this.chargerSalles();
      },

      error: error => {
        console.error('Erreur enregistrement salle', error);

        this.enregistrement = false;

        Swal.fire({
          icon: 'error',
          title: 'Échec de l’enregistrement',
          text: this.messageHttp(
            error,
            'Impossible d’enregistrer la salle.'
          ),
          confirmButtonText: 'OK',
          confirmButtonColor: '#5142a5'
        });
      }
    });
  }

  // ==========================================
  // MODIFIER UNE SALLE
  // ==========================================

  modifierSalle(salle: Salle): void {

    this.afficherFormulaire = true;
    this.modeModification = true;
    this.salleIdModifie = salle.id;

    this.nouvelleSalle = {
      nom: salle.nom,
      capacite: salle.capacite,
      type: salle.type
    };
  }

  // ==========================================
  // SUPPRIMER UNE SALLE
  // ==========================================

  supprimerSalle(id: number): void {

    Swal.fire({
      title: 'Supprimer cette salle ?',
      text: 'Cette action est irréversible.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#dc3545',
      cancelButtonColor: '#6c757d',
      confirmButtonText: 'Oui, supprimer',
      cancelButtonText: 'Annuler',
      reverseButtons: true
    }).then(result => {

      if (!result.isConfirmed) {
        return;
      }

      this.salleService.supprimer(id).subscribe({

        next: () => {

          Swal.fire({
            icon: 'success',
            title: 'Salle supprimée !',
            text: 'La salle a été supprimée avec succès.',
            confirmButtonText: 'OK',
            confirmButtonColor: '#5142a5',
            timer: 2500,
            timerProgressBar: true
          });

          this.chargerSalles();
        },

        error: error => {
          console.error('Erreur suppression salle', error);

          Swal.fire({
            icon: 'error',
            title: 'Erreur de suppression',
            text: this.messageHttp(
              error,
              'Impossible de supprimer cette salle.'
            ),
            confirmButtonText: 'OK',
            confirmButtonColor: '#5142a5'
          });
        }
      });
    });
  }

  // ==========================================
  // GESTION DES ERREURS HTTP
  // ==========================================

  private messageHttp(error: any, defaut: string): string {

    if (error.status === 400) {
      return error.error?.message || 'Les données envoyées sont invalides.';
    }

    if (error.status === 401) {
      return 'Session expirée. Veuillez vous reconnecter.';
    }

    if (error.status === 403) {
      return 'Accès refusé : réservé aux administrateurs.';
    }

    if (error.status === 404) {
      return 'Salle introuvable.';
    }

    if (error.status === 409) {
      return error.error?.message || 'Cette salle existe déjà ou est utilisée.';
    }

    if (error.status === 0) {
      return 'Serveur injoignable. Vérifiez votre connexion.';
    }

    return error.error?.message || defaut;
  }
}
