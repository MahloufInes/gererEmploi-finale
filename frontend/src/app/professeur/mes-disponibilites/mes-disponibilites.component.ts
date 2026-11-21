
import { Component } from '@angular/core';

import { AuthService } from '../../services/auth.service';

import {
  DisponibiliteService,
  CreneauSelectionne
} from '../../services/disponibilite.service';

interface Creneau {
  id: number;
  numero: number;
  heureDebut: string;
  heureFin: string;
  selected: boolean;
}

interface Jour {
  nom: string;
  icon: string;
  creneaux: Creneau[];
}

interface UtilisateurConnecte {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: string;
}

@Component({
  selector: 'app-mes-disponibilites',
  templateUrl: './mes-disponibilites.component.html',
  styleUrls: ['./mes-disponibilites.component.css']
})
export class MesDisponibilitesComponent {

  messageSucces = '';
  messageErreur = '';
  enregistrementEnCours = false;

  private horaires = [
    { debut: '08:30', fin: '10:00' },
    { debut: '10:05', fin: '11:35' },
    { debut: '11:40', fin: '13:10' },
    { debut: '13:15', fin: '14:45' },
    { debut: '14:50', fin: '16:20' },
    { debut: '16:25', fin: '17:55' }
  ];

  jours: Jour[] = [
    this.creerJour('Lundi', '📅'),
    this.creerJour('Mardi', '📅'),
    this.creerJour('Mercredi', '📅'),
    this.creerJour('Jeudi', '📅'),
    this.creerJour('Vendredi', '📅'),
    this.creerJour('Samedi', '📅')
  ];

  constructor(
    private disponibiliteService: DisponibiliteService,
    private authService: AuthService
  ) {}

  private creerJour(nom: string, icon: string): Jour {
    return {
      nom,
      icon,
      creneaux: this.horaires.map((horaire, index) => ({
        id: this.genererId(nom, index),
        numero: index + 1,
        heureDebut: horaire.debut,
        heureFin: horaire.fin,
        selected: false
      }))
    };
  }

  private genererId(jour: string, index: number): number {
    const jours = [
      'Lundi',
      'Mardi',
      'Mercredi',
      'Jeudi',
      'Vendredi',
      'Samedi'
    ];

    return jours.indexOf(jour) * 10 + index + 1;
  }

  // Nombre total de créneaux sélectionnés
  get nombreDisponibilites(): number {
    return this.jours.reduce(
      (total, jour) =>
        total + jour.creneaux.filter(c => c.selected).length,
      0
    );
  }

  // Nombre de créneaux sélectionnés pour un jour
  getNombreSelectionne(jour: Jour): number {
    return jour.creneaux.filter(
      creneau => creneau.selected
    ).length;
  }

  // Sélectionner ou désélectionner un créneau
  toggleCreneau(creneau: Creneau): void {
    if (this.enregistrementEnCours) {
      return;
    }

    creneau.selected = !creneau.selected;
    this.messageSucces = '';
    this.messageErreur = '';
  }

  // Sélectionner toutes les séances d'un jour
  selectionnerTout(jour: Jour): void {
    if (this.enregistrementEnCours) {
      return;
    }

    const tousSelectionnes = jour.creneaux.every(
      creneau => creneau.selected
    );

    jour.creneaux.forEach(creneau => {
      creneau.selected = !tousSelectionnes;
    });

    this.messageSucces = '';
    this.messageErreur = '';
  }

  // Réinitialiser toutes les disponibilités
  reinitialiser(): void {
    if (this.enregistrementEnCours) {
      return;
    }

    this.jours.forEach(jour => {
      jour.creneaux.forEach(creneau => {
        creneau.selected = false;
      });
    });

    this.messageSucces = '';
    this.messageErreur = '';
  }

  // Convertir le nom français en valeur de l'enum Java
  private convertirJour(jour: string): string {
    const correspondance: { [key: string]: string } = {
      'Lundi': 'LUNDI',
      'Mardi': 'MARDI',
      'Mercredi': 'MERCREDI',
      'Jeudi': 'JEUDI',
      'Vendredi': 'VENDREDI',
      'Samedi': 'SAMEDI'
    };

    return correspondance[jour];
  }

  // Enregistrer les disponibilités dans MySQL
  enregistrer(): void {

    this.messageSucces = '';
    this.messageErreur = '';

    if (this.nombreDisponibilites === 0) {
      this.messageErreur =
        'Veuillez sélectionner au moins un créneau.';
      return;
    }

    // Récupérer le token JWT
    const token = this.authService.getToken();

    if (!token) {
      this.messageErreur =
        'Votre session a expiré. Veuillez vous reconnecter.';
      return;
    }

    // Récupérer les informations du professeur connecté
    const utilisateurJSON = localStorage.getItem('user');

    if (!utilisateurJSON) {
      this.messageErreur =
        'Impossible de récupérer les informations du professeur.';
      return;
    }

    let utilisateur: UtilisateurConnecte;

    try {
      utilisateur = JSON.parse(utilisateurJSON);
    } catch {
      this.messageErreur =
        'Les informations de votre session sont invalides.';
      return;
    }

    if (
      !utilisateur.id ||
      utilisateur.role !== 'PROFESSEUR'
    ) {
      this.messageErreur =
        'Seul un professeur connecté peut enregistrer ses disponibilités.';
      return;
    }

    // Construire la liste des créneaux sélectionnés
    const creneauxSelectionnes: CreneauSelectionne[] = [];

    this.jours.forEach(jour => {

      jour.creneaux.forEach(creneau => {

        if (creneau.selected) {

          creneauxSelectionnes.push({
            jour: this.convertirJour(jour.nom),
            heureDebut: creneau.heureDebut,
            heureFin: creneau.heureFin
          });

        }

      });

    });

    const request = {
      creneaux: creneauxSelectionnes
    };

    this.enregistrementEnCours = true;

    // Appel au backend Spring Boot
    this.disponibiliteService
      .enregistrerDisponibilites(
        utilisateur.id,
        request,
        token
      )
      .subscribe({

        next: (response) => {

          this.enregistrementEnCours = false;

          const nombreAjoute = response?.nombreAjoute ?? 0;

          if (nombreAjoute > 0) {
            this.messageSucces =
              `${nombreAjoute} disponibilité(s) enregistrée(s) avec succès !`;
          } else {
            this.messageSucces =
              'Vos disponibilités sélectionnées existent déjà dans la base de données.';
          }

        },

        error: (error) => {

          this.enregistrementEnCours = false;

          console.error(
            'Erreur lors de l’enregistrement des disponibilités :',
            error
          );

          if (error.status === 0) {
            this.messageErreur =
              'Impossible de contacter le serveur. Vérifiez que Spring Boot est démarré.';
          } else if (error.status === 401 || error.status === 403) {
            this.messageErreur =
              'Accès refusé. Votre session a expiré ou vous ne disposez pas des droits nécessaires.';
          } else {
            this.messageErreur =
              error.error?.message ||
              'Une erreur est survenue lors de l’enregistrement.';
          }

        }

      });
  }
}
