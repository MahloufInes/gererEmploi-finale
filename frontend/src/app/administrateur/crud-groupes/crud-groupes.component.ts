
import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import Swal from 'sweetalert2';

interface Filiere {
  id: number;
  nom: string;
}

interface Groupe {
  id: number;
  nom: string;
  effectif: number;
  filiereId: number;
  filiere?: string;
  filiereNom?: string;
  semestres?: string[];
}

@Component({
  selector: 'app-crud-groupes',
  templateUrl: './crud-groupes.component.html',
  styleUrls: ['./crud-groupes.component.css']
})
export class CrudGroupesComponent implements OnInit {

  private apiGroupes =
    'https://edttt-backend-api.onrender.com/api/groupes';

  private apiFilieres =
    'https://edttt-backend-api.onrender.com/api/filieres';

  readonly semestresDisponibles = [
    'S1', 'S2', 'S3', 'S4', 'S5', 'S6'
  ];

  groupes: Groupe[] = [];
  filieres: Filiere[] = [];

  showForm = false;
  editingId: number | null = null;
  search = '';
  erreur = '';
  chargement = false;

  form: {
    nom: string;
    effectif: number;
    filiereId: number;
    semestres: string[];
  } = {
    nom: '',
    effectif: 1,
    filiereId: 0,
    semestres: []
  };

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.chargerGroupes();
    this.chargerFilieres();
  }

  // CHARGER LES GROUPES
  chargerGroupes(): void {
    this.chargement = true;

    this.http.get<Groupe[]>(this.apiGroupes).subscribe({
      next: data => {
        this.groupes = data;
        this.chargement = false;
      },
      error: err => {
        console.error(err);
        this.chargement = false;

        Swal.fire({
          icon: 'error',
          title: 'Erreur de chargement',
          text: 'Impossible de charger les groupes.',
          confirmButtonColor: '#5142a5'
        });
      }
    });
  }

  // CHARGER LES FILIERES
  chargerFilieres(): void {
    this.http.get<Filiere[]>(this.apiFilieres).subscribe({
      next: data => {
        this.filieres = data;
      },
      error: err => {
        console.error(err);

        Swal.fire({
          icon: 'error',
          title: 'Erreur',
          text: 'Impossible de charger les filières.',
          confirmButtonColor: '#5142a5'
        });
      }
    });
  }

  // RECUPERER LE NOM DE LA FILIERE
  nomFiliere(g: Groupe): string {
    return g.filiere ?? g.filiereNom ?? '';
  }

  // RECHERCHE
  get filtered(): Groupe[] {
    const q = this.search.toLowerCase().trim();

    return this.groupes.filter(g =>
      (g.nom ?? '').toLowerCase().includes(q) ||
      this.nomFiliere(g).toLowerCase().includes(q)
    );
  }

  // OUVRIR LE FORMULAIRE D'AJOUT
  openAdd(): void {
    this.editingId = null;

    this.form = {
      nom: '',
      effectif: 1,
      filiereId: this.filieres.length
        ? this.filieres[0].id
        : 0,
      semestres: []
    };

    this.erreur = '';
    this.showForm = true;
  }

  // OUVRIR LE FORMULAIRE DE MODIFICATION
  openEdit(groupe: Groupe): void {
    this.editingId = groupe.id;

    this.form = {
      nom: groupe.nom,
      effectif: groupe.effectif,
      filiereId: groupe.filiereId,
      semestres: [...(groupe.semestres ?? [])]
    };

    this.erreur = '';
    this.showForm = true;
  }

  // SELECTIONNER OU DESELECTIONNER UN SEMESTRE
  toggleSemestre(s: string): void {
    this.form.semestres = this.form.semestres.includes(s)
      ? this.form.semestres.filter(x => x !== s)
      : [...this.form.semestres, s].sort();
  }

  // AJOUTER OU MODIFIER UN GROUPE
  save(): void {

    // VERIFICATION DES CHAMPS
    if (
      !this.form.nom.trim() ||
      !Number.isFinite(Number(this.form.effectif)) ||
      this.form.effectif < 1 ||
      this.form.filiereId <= 0
    ) {
      Swal.fire({
        icon: 'warning',
        title: 'Champs obligatoires',
        text: 'Veuillez remplir correctement tous les champs.',
        confirmButtonColor: '#5142a5'
      });
      return;
    }

    // VERIFICATION DES SEMESTRES
    if (this.form.semestres.length === 0) {
      Swal.fire({
        icon: 'warning',
        title: 'Semestres obligatoires',
        text: 'Veuillez choisir au moins un semestre.',
        confirmButtonColor: '#5142a5'
      });
      return;
    }

    this.erreur = '';

    const request = {
      nom: this.form.nom.trim(),
      effectif: Number(this.form.effectif),
      filiereId: Number(this.form.filiereId),
      semestres: this.form.semestres
    };

    if (this.editingId === null) {

      // AJOUT D'UN GROUPE
      this.http.post(this.apiGroupes, request).subscribe({
        next: () => {
          this.close();
          this.chargerGroupes();

          Swal.fire({
            icon: 'success',
            title: 'Groupe ajouté !',
            text: 'Le groupe a été ajouté avec succès.',
            confirmButtonColor: '#5142a5'
          });
        },
        error: err => {
          console.error(err);

          Swal.fire({
            icon: 'error',
            title: 'Erreur lors de l’ajout',
            text: this.messageErreur(
              err,
              'Erreur lors de l’ajout du groupe.'
            ),
            confirmButtonColor: '#5142a5'
          });
        }
      });

    } else {

      // MODIFICATION D'UN GROUPE
      this.http.put(
        `${this.apiGroupes}/${this.editingId}`,
        request
      ).subscribe({
        next: () => {
          this.close();
          this.chargerGroupes();

          Swal.fire({
            icon: 'success',
            title: 'Modification réussie !',
            text: 'Le groupe a été modifié avec succès.',
            confirmButtonColor: '#5142a5'
          });
        },
        error: err => {
          console.error(err);

          Swal.fire({
            icon: 'error',
            title: 'Erreur lors de la modification',
            text: this.messageErreur(
              err,
              'Erreur lors de la modification du groupe.'
            ),
            confirmButtonColor: '#5142a5'
          });
        }
      });
    }
  }

  // SUPPRIMER UN GROUPE
  supprimer(groupe: Groupe): void {

    Swal.fire({
      title: 'Êtes-vous sûr ?',
      text: `Voulez-vous supprimer le groupe ${groupe.nom} ?`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#d33',
      cancelButtonColor: '#6c757d',
      confirmButtonText: 'Oui, supprimer',
      cancelButtonText: 'Annuler'
    }).then(result => {

      if (result.isConfirmed) {

        this.http.delete(
          `${this.apiGroupes}/${groupe.id}`
        ).subscribe({
          next: () => {
            this.chargerGroupes();

            Swal.fire({
              icon: 'success',
              title: 'Groupe supprimé !',
              text: 'Le groupe a été supprimé avec succès.',
              confirmButtonColor: '#5142a5'
            });
          },
          error: err => {
            console.error(err);

            Swal.fire({
              icon: 'error',
              title: 'Impossible de supprimer',
              text: this.messageErreur(
                err,
                'Impossible de supprimer ce groupe.'
              ),
              confirmButtonColor: '#5142a5'
            });
          }
        });
      }
    });
  }

  // FERMER LE FORMULAIRE
  close(): void {
    this.showForm = false;
    this.editingId = null;

    this.form = {
      nom: '',
      effectif: 1,
      filiereId: 0,
      semestres: []
    };

    this.erreur = '';
  }

  // GESTION DES MESSAGES D'ERREUR
  private messageErreur(err: any, defaut: string): string {
    if (err.status === 401) {
      return 'Session expirée. Reconnectez-vous.';
    }

    if (err.status === 403) {
      return 'Accès refusé : réservé aux administrateurs.';
    }

    return err.error?.message || defaut;
  }
}
  