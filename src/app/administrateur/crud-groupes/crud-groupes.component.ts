import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface Filiere {
  id: number;
  nom: string;
}

interface Groupe {
  id: number;
  nom: string;
  effectif: number;
  filiereId: number;
  filiere?: string;      // name of the filiere (depends on your GroupeResponse)
  filiereNom?: string;   // alternative field name
  semestres?: string[];
}

@Component({
  selector: 'app-crud-groupes',
  templateUrl: './crud-groupes.component.html',
  styleUrls: ['./crud-groupes.component.css']
})
export class CrudGroupesComponent implements OnInit {

  private apiGroupes = 'http://localhost:8081/api/groupes';
  private apiFilieres = 'http://localhost:8081/api/filieres';

  readonly semestresDisponibles = ['S1', 'S2', 'S3', 'S4', 'S5', 'S6'];

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

  chargerGroupes(): void {
    this.chargement = true;

    this.http.get<Groupe[]>(this.apiGroupes).subscribe({
      next: data => {
        this.groupes = data;
        this.chargement = false;
      },
      error: err => {
        console.error(err);
        this.erreur = 'Impossible de charger les groupes.';
        this.chargement = false;
      }
    });
  }

  chargerFilieres(): void {
    this.http.get<Filiere[]>(this.apiFilieres).subscribe({
      next: data => this.filieres = data,
      error: err => {
        console.error(err);
        this.erreur = 'Impossible de charger les filières.';
      }
    });
  }

  nomFiliere(g: Groupe): string {
    return g.filiere ?? g.filiereNom ?? '';
  }

  get filtered(): Groupe[] {
    const q = this.search.toLowerCase().trim();

    return this.groupes.filter(g =>
      (g.nom ?? '').toLowerCase().includes(q) ||
      this.nomFiliere(g).toLowerCase().includes(q)
    );
  }

  openAdd(): void {
    this.editingId = null;
    this.form = {
      nom: '',
      effectif: 1,
      filiereId: this.filieres.length ? this.filieres[0].id : 0,
      semestres: []
    };
    this.erreur = '';
    this.showForm = true;
  }

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

  toggleSemestre(s: string): void {
    this.form.semestres = this.form.semestres.includes(s)
      ? this.form.semestres.filter(x => x !== s)
      : [...this.form.semestres, s].sort();
  }

  save(): void {
    if (
      !this.form.nom.trim() ||
      this.form.effectif < 1 ||
      this.form.filiereId <= 0
    ) {
      this.erreur = 'Veuillez remplir correctement tous les champs.';
      return;
    }

    if (this.form.semestres.length === 0) {
      this.erreur = 'Veuillez choisir au moins un semestre.';
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
      this.http.post(this.apiGroupes, request).subscribe({
        next: () => {
          this.close();
          this.chargerGroupes();
        },
        error: err => {
          console.error(err);
          this.erreur = this.messageErreur(err, 'Erreur lors de l’ajout.');
        }
      });
    } else {
      this.http.put(`${this.apiGroupes}/${this.editingId}`, request).subscribe({
        next: () => {
          this.close();
          this.chargerGroupes();
        },
        error: err => {
          console.error(err);
          this.erreur = this.messageErreur(err, 'Erreur lors de la modification.');
        }
      });
    }
  }

  supprimer(groupe: Groupe): void {
    const confirmation = confirm(
      `Voulez-vous supprimer le groupe ${groupe.nom} ?`
    );

    if (!confirmation) return;

    this.http.delete(`${this.apiGroupes}/${groupe.id}`).subscribe({
      next: () => this.chargerGroupes(),
      error: err => {
        console.error(err);
        this.erreur = this.messageErreur(err, 'Impossible de supprimer ce groupe.');
      }
    });
  }

  close(): void {
    this.showForm = false;
    this.editingId = null;
  }

  private messageErreur(err: any, defaut: string): string {
    if (err.status === 401) return 'Session expirée. Reconnectez-vous.';
    if (err.status === 403) return 'Accès refusé : réservé aux administrateurs.';
    return err.error?.message || defaut;
  }
}