
import { Component, OnInit } from '@angular/core';

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
export class MesDisponibilitesComponent implements OnInit {

  messageSucces = '';
  messageErreur = '';
  enregistrementEnCours = false;
  chargement = false;

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

  // Charger les disponibilités dès l'ouverture de la page
  ngOnInit(): void {
    this.chargerDisponibilites();
  }

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

  // Récupérer le professeur connecté
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
    return jour.creneaux.filter(c => c.selected).length;
  }

  // Conversion du jour Java vers le jour français
  private convertirJourFrancais(jour: string): string {

    const correspondance: { [key: string]: string } = {
      'LUNDI': 'Lundi',
      'MARDI': 'Mardi',
      'MERCREDI': 'Mercredi',
      'JEUDI': 'Jeudi',
      'VENDREDI': 'Vendredi',
      'SAMEDI': 'Samedi'
    };

    return correspondance[jour.toUpperCase()] || jour;
  }

  // Charger les disponibilités depuis MySQL
  chargerDisponibilites(): void {

    this.messageErreur = '';
    this.messageSucces = '';

    const token = this.authService.getToken();
    const utilisateur = this.obtenirUtilisateur();

    if (!token) {
      this.messageErreur =
        'Votre session a expiré. Veuillez vous reconnecter.';
      return;
    }

    if (
      !utilisateur ||
      !utilisateur.id ||
      utilisateur.role !== 'PROFESSEUR'
    ) {
      this.messageErreur =
        'Impossible de récupérer les informations du professeur.';
      return;
    }

    this.chargement = true;

    this.disponibiliteService
      .getDisponibilites(utilisateur.id, token)
      .subscribe({

        next: (disponibilites: CreneauSelectionne[]) => {

          // Tout décocher avant de restaurer les sélections
          this.jours.forEach(jour => {
            jour.creneaux.forEach(creneau => {
              creneau.selected = false;
            });
          });

          // Restaurer les créneaux enregistrés
          disponibilites.forEach(disponibilite => {

            const nomJour = this.convertirJourFrancais(
              disponibilite.jour
            );

            const jour = this.jours.find(
              j => j.nom === nomJour
            );

            if (!jour) {
              return;
            }

            const heureDebut = disponibilite.heureDebut.substring(0, 5);
            const heureFin = disponibilite.heureFin.substring(0, 5);

            const creneau = jour.creneaux.find(
              c =>
                c.heureDebut === heureDebut &&
                c.heureFin === heureFin
            );

            if (creneau) {
              creneau.selected = true;
            }
          });

          this.chargement = false;

          console.log(
            'Disponibilités récupérées :',
            disponibilites
          );
        },

        error: (error) => {

          this.chargement = false;

          console.error(
            'Erreur lors du chargement des disponibilités :',
            error
          );

          if (error.status === 0) {
            this.messageErreur =
              'Impossible de contacter le serveur.';
          } else if (
            error.status === 401 ||
            error.status === 403
          ) {
            this.messageErreur =
              'Accès refusé. Veuillez vous reconnecter.';
          } else {
            this.messageErreur =
              error.error?.message ||
              'Erreur lors du chargement des disponibilités.';
          }
        }
      });
  }

  // Sélectionner ou désélectionner un créneau
  toggleCreneau(creneau: Creneau): void {

    if (this.enregistrementEnCours || this.chargement) {
      return;
    }

    creneau.selected = !creneau.selected;

    this.messageSucces = '';
    this.messageErreur = '';
  }

  // Sélectionner toutes les séances d'un jour
  selectionnerTout(jour: Jour): void {

    if (this.enregistrementEnCours || this.chargement) {
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

    if (this.enregistrementEnCours || this.chargement) {
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

    const token = this.authService.getToken();
    const utilisateur = this.obtenirUtilisateur();

    if (!token) {
      this.messageErreur =
        'Votre session a expiré. Veuillez vous reconnecter.';
      return;
    }

    if (
      !utilisateur ||
      !utilisateur.id ||
      utilisateur.role !== 'PROFESSEUR'
    ) {
      this.messageErreur =
        'Impossible de récupérer les informations du professeur.';
      return;
    }

    // Construire la nouvelle liste des créneaux sélectionnés
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

    this.disponibiliteService
      .enregistrerDisponibilites(
        utilisateur.id,
        request,
        token
      )
      .subscribe({

        next: (response) => {

          this.enregistrementEnCours = false;

          this.messageSucces =
            `${response.nombreAjoute} disponibilité(s) enregistrée(s) avec succès !`;

        },

        error: (error) => {

          this.enregistrementEnCours = false;

          console.error(
            'Erreur lors de l’enregistrement :',
            error
          );

          if (error.status === 0) {
            this.messageErreur =
              'Impossible de contacter le serveur. Vérifiez que Spring Boot est démarré.';
          } else if (
            error.status === 401 ||
            error.status === 403
          ) {
            this.messageErreur =
              'Accès refusé. Veuillez vous reconnecter.';
          } else {
            this.messageErreur =
              error.error?.message ||
              'Une erreur est survenue lors de l’enregistrement.';
          }

        }
      });
  }
}
