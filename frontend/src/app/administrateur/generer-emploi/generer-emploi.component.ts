
import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import Swal from 'sweetalert2';

interface Filiere {
  id: number;
  nom: string;
}

interface GenerationResponse {
  emploiDuTempsId: number;
  semestre: string;
  nombreSeances: number;
  message: string;
}

interface Seance {
  id?: number;
  date?: string | null;
  jour: string;
  heureDebut: string;
  heureFin: string;
  type?: string;
  statut?: string;
  matiereNom: string;
  professeurNom: string;
  salleNom: string;
  groupeNom: string;
}

interface Horaire {
  debut: string;
  fin: string;
}

@Component({
  selector: 'app-generer-emploi',
  templateUrl: './generer-emploi.component.html',
  styleUrls: ['./generer-emploi.component.css']
})
export class GenererEmploiComponent implements OnInit {

  private apiFilieres =
    'https://edttt-backend-api.onrender.com/api/filieres';

  private apiGeneration =
    'https://edttt-backend-api.onrender.com/api/generation/generer';

  private apiSeances =
    'https://edttt-backend-api.onrender.com/api/generation/emploi';

  filieres: Filiere[] = [];
  seances: Seance[] = [];

  filiereId: number | null = null;
  semestre = 'S1';

  chargementFilieres = false;
  chargementSeances = false;
  generationEnCours = false;

  erreur = '';
  succes = '';

  resultat: GenerationResponse | null = null;

  administrateurId = 1;

  jours = [
    'LUNDI',
    'MARDI',
    'MERCREDI',
    'JEUDI',
    'VENDREDI',
    'SAMEDI'
  ];

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.chargerFilieres();
  }

  // ==========================================
  // SÉANCES PAR GROUPE
  // ==========================================

  get seancesParGroupe(): [string, Seance[]][] {
    const map: { [groupe: string]: Seance[] } = {};

    for (const s of this.seances) {
      const cle = s.groupeNom || 'Sans groupe';
      (map[cle] ||= []).push(s);
    }

    return Object.entries(map).sort(
      (a, b) => a[0].localeCompare(b[0])
    );
  }

  // ==========================================
  // HORAIRES
  // ==========================================

  get horaires(): Horaire[] {
    const map = new Map<string, string>();

    for (const s of this.seances) {
      map.set(s.heureDebut, s.heureFin);
    }

    return Array.from(map.entries())
      .map(([debut, fin]) => ({ debut, fin }))
      .sort((a, b) => a.debut.localeCompare(b.debut));
  }

  // ==========================================
  // UTILITAIRES
  // ==========================================

  normaliserJour(jour: string): string {
    return (jour || '')
      .toUpperCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
  }

  trouverSeance(
    groupeSeances: Seance[],
    jour: string,
    horaire: Horaire
  ): Seance | undefined {

    return groupeSeances.find(s =>
      this.normaliserJour(s.jour) === jour &&
      s.heureDebut === horaire.debut
    );
  }

  afficherType(type?: string): string {
    const valeur = (type || '').toUpperCase();

    if (valeur.includes('TP')) {
      return 'TP';
    }

    if (valeur.includes('COURS')) {
      return 'COURS';
    }

    return valeur || 'SÉANCE';
  }

  // ==========================================
  // CHARGER LES FILIÈRES
  // ==========================================

  chargerFilieres(): void {
    this.chargementFilieres = true;
    this.erreur = '';

    this.http.get<Filiere[]>(this.apiFilieres).subscribe({
      next: data => {
        this.filieres = data;
        this.chargementFilieres = false;
      },
      error: err => {
        console.error('Erreur chargement filières :', err);

        this.erreur = 'Impossible de charger les filières.';
        this.chargementFilieres = false;

        Swal.fire({
          icon: 'error',
          title: 'Erreur de chargement',
          text: this.extraireErreur(err),
          confirmButtonText: 'OK'
        });
      }
    });
  }

  // ==========================================
  // GÉNÉRER L'EMPLOI DU TEMPS
  // ==========================================

  generer(): void {

    if (this.filiereId === null) {
      Swal.fire({
        icon: 'warning',
        title: 'Filière obligatoire',
        text: 'Veuillez sélectionner une filière avant de continuer.',
        confirmButtonText: 'OK'
      });

      return;
    }

    this.erreur = '';
    this.succes = '';
    this.resultat = null;
    this.seances = [];
    this.generationEnCours = true;

    const params = new HttpParams()
      .set('filiereId', this.filiereId.toString())
      .set('semestre', this.semestre)
      .set('administrateurId', this.administrateurId.toString());

    this.http.post<GenerationResponse>(
      this.apiGeneration,
      null,
      { params }
    ).subscribe({

      next: response => {
        this.resultat = response;
        this.succes = response.message;
        this.generationEnCours = false;

        Swal.fire({
          icon: 'success',
          title: 'Génération réussie !',
          text: response.message ||
            'L’emploi du temps a été généré avec succès.',
          confirmButtonText: 'Parfait',
          timer: 3000,
          timerProgressBar: true
        });

        this.chargerSeances(response.emploiDuTempsId);
      },

      error: err => {
        console.error('Erreur génération :', err);

        this.erreur = this.extraireErreur(err);
        this.generationEnCours = false;

        Swal.fire({
          icon: 'error',
          title: 'Échec de la génération',
          text: this.erreur,
          confirmButtonText: 'OK'
        });
      }
    });
  }

  // ==========================================
  // CHARGER LES SÉANCES
  // ==========================================

  chargerSeances(emploiId: number): void {
    this.chargementSeances = true;

    this.http.get<Seance[]>(
      `${this.apiSeances}/${emploiId}/seances`
    ).subscribe({

      next: data => {
        this.seances = data;
        this.chargementSeances = false;
      },

      error: err => {
        console.error('Erreur chargement séances :', err);

        this.erreur =
          'L’emploi du temps est généré, mais impossible de charger les séances.';

        this.chargementSeances = false;

        Swal.fire({
          icon: 'warning',
          title: 'Emploi du temps généré',
          text: this.erreur,
          confirmButtonText: 'OK'
        });
      }
    });
  }

  // ==========================================
  // EXTRACTION DES ERREURS HTTP
  // ==========================================

  private extraireErreur(err: any): string {
    const backendError = err.error;

    if (typeof backendError === 'string') {
      return backendError;
    }

    if (backendError && typeof backendError === 'object') {
      return backendError.message
        || backendError.error
        || JSON.stringify(backendError);
    }

    if (err.status === 401) {
      return 'Session expirée. Veuillez vous reconnecter.';
    }

    if (err.status === 403) {
      return 'Accès refusé. Vous devez être administrateur.';
    }

    if (err.status === 0) {
      return 'Impossible de contacter le serveur. Vérifiez votre connexion.';
    }

    return err.message
      || 'Une erreur est survenue pendant la génération de l’emploi du temps.';
  }
}
