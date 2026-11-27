
import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Matiere, MatiereService } from '../services/matiere.service';
import Swal from 'sweetalert2';

interface Departement {
  id: number;
  nom: string;
}

@Component({
  selector: 'app-matieres',
  templateUrl: './matieres.component.html',
  styleUrls: ['./matieres.component.css']
})
export class MatieresComponent implements OnInit {

  matieres: Matiere[] = [];
  departements: Departement[] = [];

  search = '';
  chargement = false;
  erreur = '';

  showForm = false;
  editingId: number | null = null;

  form: { nom: string; departementId: number } = {
    nom: '',
    departementId: 0
  };

  constructor(
    private matiereService: MatiereService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.chargerDepartements();
    this.charger();
  }

  // ==========================================
  // CHARGER LES DÉPARTEMENTS
  // ==========================================

  chargerDepartements(): void {
    this.http.get<Departement[]>(
      'https://edttt-backend-api.onrender.com/api/departements'
    ).subscribe({
      next: d => {
        this.departements = d;
      },
      error: err => {
        console.error('Erreur chargement départements :', err);

        Swal.fire({
          icon: 'error',
          title: 'Erreur de chargement',
          text: this.messageHttp(
            err,
            'Impossible de charger les départements.'
          ),
          confirmButtonText: 'OK',
          confirmButtonColor: '#5142a5'
        });
      }
    });
  }

  // ==========================================
  // CHARGER LES MATIÈRES
  // ==========================================

  charger(): void {
    this.chargement = true;
    this.erreur = '';

    this.matiereService.getAll().subscribe({
      next: m => {
        this.matieres = m;
        this.chargement = false;
      },
      error: err => {
        console.error('Erreur chargement matières :', err);

        this.chargement = false;
        this.erreur = this.messageHttp(
          err,
          'Erreur de chargement des matières.'
        );

        Swal.fire({
          icon: 'error',
          title: 'Erreur de chargement',
          text: this.erreur,
          confirmButtonText: 'OK',
          confirmButtonColor: '#5142a5'
        });
      }
    });
  }

  // ==========================================
  // RECHERCHE ET FILTRAGE
  // ==========================================

  get filtered(): Matiere[] {
    const q = this.search.trim().toLowerCase();

    return this.matieres.filter(
      m => !q || m.nom.toLowerCase().includes(q)
    );
  }

  // ==========================================
  // OUVRIR LE FORMULAIRE D'AJOUT
  // ==========================================

  openAdd(): void {
    this.editingId = null;

    this.form = {
      nom: '',
      departementId: 0
    };

    this.erreur = '';
    this.showForm = true;
  }

  // ==========================================
  // OUVRIR LE FORMULAIRE DE MODIFICATION
  // ==========================================

  openEdit(m: Matiere): void {
    this.editingId = m.id;

    this.form = {
      nom: m.nom,
      departementId: m.departementId ?? 0
    };

    this.erreur = '';
    this.showForm = true;
  }

  // ==========================================
  // FERMER LE FORMULAIRE
  // ==========================================

  close(): void {
    this.showForm = false;
    this.erreur = '';

    this.form = {
      nom: '',
      departementId: 0
    };

    this.editingId = null;
  }

  // ==========================================
  // AJOUTER OU MODIFIER UNE MATIÈRE
  // ==========================================

  save(): void {

    // Vérification du nom
    if (!this.form.nom.trim()) {
      Swal.fire({
        icon: 'warning',
        title: 'Nom obligatoire',
        text: 'Veuillez saisir le nom de la matière.',
        confirmButtonText: 'OK',
        confirmButtonColor: '#5142a5'
      });

      return;
    }

    // Vérification du département
    if (!this.form.departementId ||
        Number(this.form.departementId) <= 0) {

      Swal.fire({
        icon: 'warning',
        title: 'Département obligatoire',
        text: 'Veuillez choisir un département.',
        confirmButtonText: 'OK',
        confirmButtonColor: '#5142a5'
      });

      return;
    }

    const body = {
      nom: this.form.nom.trim(),
      departementId: Number(this.form.departementId)
    };

    const modification = this.editingId !== null;

    const requete = modification
      ? this.matiereService.modifier(this.editingId!, body)
      : this.matiereService.ajouter(body);

    requete.subscribe({

      next: () => {

        this.close();

        Swal.fire({
          icon: 'success',
          title: modification
            ? 'Matière modifiée !'
            : 'Matière ajoutée !',

          text: modification
            ? 'Les modifications ont été enregistrées avec succès.'
            : 'La nouvelle matière a été ajoutée avec succès.',

          confirmButtonText: 'Parfait !',
          confirmButtonColor: '#5142a5',
          timer: 2500,
          timerProgressBar: true
        });

        this.charger();
      },

      error: err => {
        console.error('Erreur enregistrement matière :', err);

        Swal.fire({
          icon: 'error',
          title: 'Échec de l’enregistrement',
          text: this.messageHttp(
            err,
            'Erreur lors de l’enregistrement de la matière.'
          ),
          confirmButtonText: 'OK',
          confirmButtonColor: '#5142a5'
        });
      }
    });
  }

  // ==========================================
  // SUPPRIMER UNE MATIÈRE
  // ==========================================

  supprimer(m: Matiere): void {

    Swal.fire({
      title: 'Supprimer cette matière ?',
      text: `Voulez-vous vraiment supprimer la matière « ${m.nom} » ?`,
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

      this.matiereService.supprimer(m.id).subscribe({

        next: () => {

          Swal.fire({
            icon: 'success',
            title: 'Matière supprimée !',
            text: 'La matière a été supprimée avec succès.',
            confirmButtonText: 'OK',
            confirmButtonColor: '#5142a5',
            timer: 2500,
            timerProgressBar: true
          });

          this.charger();
        },

        error: err => {
          console.error('Erreur suppression matière :', err);

          Swal.fire({
            icon: 'error',
            title: 'Erreur de suppression',
            text: this.messageHttp(
              err,
              'Impossible de supprimer cette matière.'
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

  private messageHttp(err: any, defaut: string): string {

    if (err.status === 400) {
      return err.error?.message || 'Les données envoyées sont invalides.';
    }

    if (err.status === 401) {
      return 'Session expirée. Veuillez vous reconnecter.';
    }

    if (err.status === 403) {
      return 'Accès refusé : réservé aux administrateurs.';
    }

    if (err.status === 404) {
      return 'Ressource introuvable.';
    }

    if (err.status === 409) {
      return err.error?.message || 'Cette matière existe déjà ou est utilisée.';
    }

    if (err.status === 0) {
      return 'Serveur injoignable. Vérifiez votre connexion.';
    }

    return err.error?.message || defaut;
  }
}
