
package com.edt.service;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

import com.edt.dto.SeanceResponse;
import com.edt.entity.*;
import com.edt.entity.enums.StatutSeance;
import com.edt.entity.enums.TypeSalle;
import com.edt.entity.enums.TypeSeance;

import com.edt.repository.EnseignementRepository;
import com.edt.repository.GroupeRepository;
import com.edt.repository.SalleRepository;
import com.edt.repository.CreneauHoraireRepository;
import com.edt.repository.DisponibiliteRepository;
import com.edt.repository.EmploiDuTempsRepository;
import com.edt.repository.SeanceRepository;
import com.edt.repository.FiliereRepository;
import com.edt.repository.AdministrateurRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GenerationEmploiDuTempsService {

    private final EnseignementService enseignementService;

    private final EnseignementRepository enseignementRepository;
    private final GroupeRepository groupeRepository;
    private final SalleRepository salleRepository;
    private final CreneauHoraireRepository creneauHoraireRepository;
    private final DisponibiliteRepository disponibiliteRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final SeanceRepository seanceRepository;
    private final FiliereRepository filiereRepository;
    private final AdministrateurRepository administrateurRepository;


    // =========================================================
    // 1. CLASSES INTERNES
    // =========================================================

    private static class SeanceAPlacer {

        private Enseignement enseignement;
        private Groupe groupe;
        private TypeSeance type;

        public SeanceAPlacer(
                Enseignement enseignement,
                Groupe groupe,
                TypeSeance type
        ) {
            this.enseignement = enseignement;
            this.groupe = groupe;
            this.type = type;
        }
    }


    private static class OccupationPlanning {

        private final Set<String> professeurs = new HashSet<>();
        private final Set<String> groupes = new HashSet<>();
        private final Set<String> salles = new HashSet<>();

        // Total des créneaux de 90 minutes occupés par groupe et par jour
        private final Map<String, Integer> chargeGroupeParJour = new HashMap<>();

        // Nombre de blocs TP par groupe et par jour
        private final Map<String, Integer> tpParGroupeParJour = new HashMap<>();

        // Matières déjà programmées par groupe et par jour
        private final Set<String> matieresTPParJour = new HashSet<>();
        private final Set<String> matieresCoursParJour = new HashSet<>();
    }


    // =========================================================
    // 2. GESTION DES CLÉS ET DES STATISTIQUES
    // =========================================================

    private String cleGroupeJour(
            Long groupeId,
            CreneauHoraire creneau
    ) {
        return groupeId + "-" + creneau.getJour().name();
    }


    private String cleMatiereJour(
            Long groupeId,
            Long matiereId,
            CreneauHoraire creneau
    ) {
        return groupeId + "-"
                + matiereId + "-"
                + creneau.getJour().name();
    }


    private int getChargeJour(
            OccupationPlanning occupation,
            Long groupeId,
            CreneauHoraire creneau
    ) {
        String key = cleGroupeJour(groupeId, creneau);

        return occupation.chargeGroupeParJour.getOrDefault(key, 0);
    }


    private int getNombreTPJour(
            OccupationPlanning occupation,
            Long groupeId,
            CreneauHoraire creneau
    ) {
        String key = cleGroupeJour(groupeId, creneau);

        return occupation.tpParGroupeParJour.getOrDefault(key, 0);
    }


    private void enregistrerCours(
            OccupationPlanning occupation,
            Long groupeId,
            Long matiereId,
            CreneauHoraire creneau
    ) {
        String jourKey = cleGroupeJour(groupeId, creneau);
        String matiereKey = cleMatiereJour(
                groupeId,
                matiereId,
                creneau
        );

        occupation.chargeGroupeParJour.merge(
                jourKey,
                1,
                Integer::sum
        );

        occupation.matieresCoursParJour.add(matiereKey);
    }


    private void enregistrerTP(
            OccupationPlanning occupation,
            Long groupeId,
            Long matiereId,
            CreneauHoraire premier,
            CreneauHoraire deuxieme
    ) {
        String jourKey = cleGroupeJour(groupeId, premier);

        String matiereKey = cleMatiereJour(
                groupeId,
                matiereId,
                premier
        );

        // Un TP occupe deux créneaux de 90 minutes
        occupation.chargeGroupeParJour.merge(
                jourKey,
                2,
                Integer::sum
        );

        // Un seul bloc TP est comptabilisé
        occupation.tpParGroupeParJour.merge(
                jourKey,
                1,
                Integer::sum
        );

        occupation.matieresTPParJour.add(matiereKey);
    }


    // =========================================================
    // 3. VALIDATION DES DONNÉES
    // =========================================================

    private void verifierDonneesGeneration(
            Long filiereId,
            String semestre
    ) {

        var filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() ->
                        new RuntimeException("Filière introuvable")
                );

        var groupes = groupeRepository.findByFiliereIdAndSemestre(
                filiere.getId(),
                semestre
        );

        if (groupes.isEmpty()) {
            throw new RuntimeException(
                    "Aucun groupe trouvé pour cette filière et ce semestre"
            );
        }

        var creneaux = creneauHoraireRepository.findAll();

        if (creneaux.isEmpty()) {
            throw new RuntimeException(
                    "Aucun créneau horaire enregistré"
            );
        }

        var salles = salleRepository.findAll();

        if (salles.isEmpty()) {
            throw new RuntimeException(
                    "Aucune salle enregistrée"
            );
        }
    }


    private int calculerNombreSeances(
            Float heures,
            float dureeSeance,
            String typeSeance
    ) {

        if (heures == null || heures < 0) {
            throw new RuntimeException(
                    "Nombre d'heures invalide pour " + typeSeance
            );
        }

        int nombre = Math.round(heures / dureeSeance);

        if (Math.abs(heures - (nombre * dureeSeance)) > 0.001f) {
            throw new RuntimeException(
                    "Les heures de " + typeSeance
                            + " doivent être un multiple de "
                            + dureeSeance
            );
        }

        return nombre;
    }


    // =========================================================
    // 4. PRÉPARATION DES SÉANCES
    // =========================================================

    /**
     * Utilisée uniquement lors de la génération initiale.
     * Génère les affectations automatiques.
     */
    private List<SeanceAPlacer> preparerSeances(
            Long filiereId,
            String semestre
    ) {

        List<Enseignement> enseignements =
                enseignementService.genererAffectationsAutomatiques(
                        filiereId,
                        semestre
                );

        return construireSeances(enseignements);
    }


    /**
     * Utilisée lors de la modification des disponibilités.
     * Récupère les affectations automatiques existantes.
     * Ne modifie aucune affectation professeur/matière/groupe.
     */
    private List<SeanceAPlacer> preparerSeancesExistantes(
            Long filiereId,
            String semestre
    ) {

        List<Enseignement> enseignements =
                enseignementRepository
                        .findAffectationsAutomatiquesByFiliereAndSemestre(
                                filiereId,
                                semestre
                        );

        return construireSeances(enseignements);
    }


    /**
     * Transforme les affectations en séances à placer.
     */
    private List<SeanceAPlacer> construireSeances(
            List<Enseignement> enseignements
    ) {

        List<SeanceAPlacer> seances = new ArrayList<>();

        if (enseignements == null || enseignements.isEmpty()) {
            throw new RuntimeException(
                    "Aucune affectation automatique trouvée pour cette filière et ce semestre."
            );
        }

        for (Enseignement enseignement : enseignements) {

            Groupe groupe = enseignement.getGroupe();

            if (groupe == null) {
                throw new RuntimeException(
                        "Aucun groupe associé à l'affectation "
                                + enseignement.getId()
                );
            }

            int nombreCours = calculerNombreSeances(
                    enseignement.getHeuresCours(),
                    1.5f,
                    "COURS"
            );

            int nombreTP = calculerNombreSeances(
                    enseignement.getHeuresTP(),
                    3f,
                    "TP"
            );

            for (int i = 0; i < nombreCours; i++) {

                seances.add(
                        new SeanceAPlacer(
                                enseignement,
                                groupe,
                                TypeSeance.COURS
                        )
                );
            }

            for (int i = 0; i < nombreTP; i++) {

                seances.add(
                        new SeanceAPlacer(
                                enseignement,
                                groupe,
                                TypeSeance.TP
                        )
                );
            }
        }

        return seances;
    }


    // =========================================================
    // 5. RECHERCHE DES CRÉNEAUX TP
    // =========================================================

    private List<List<CreneauHoraire>> trouverPairesCreneauxTP(
            Long professeurId,
            List<CreneauHoraire> creneaux
    ) {

        List<List<CreneauHoraire>> paires = new ArrayList<>();

        creneaux.sort(
                Comparator.comparing(CreneauHoraire::getJour)
                        .thenComparing(CreneauHoraire::getHeureDebut)
        );

        for (int i = 0; i < creneaux.size(); i++) {

            CreneauHoraire premier = creneaux.get(i);

            long duree1 = Duration.between(
                    premier.getHeureDebut(),
                    premier.getHeureFin()
            ).toMinutes();

            if (duree1 != 90) {
                continue;
            }

            for (int j = i + 1; j < creneaux.size(); j++) {

                CreneauHoraire deuxieme = creneaux.get(j);

                if (premier.getJour() != deuxieme.getJour()) {
                    continue;
                }

                // Écart obligatoire de 5 minutes
                if (!premier.getHeureFin()
                        .plusMinutes(5)
                        .equals(deuxieme.getHeureDebut())) {
                    continue;
                }

                long duree2 = Duration.between(
                        deuxieme.getHeureDebut(),
                        deuxieme.getHeureFin()
                ).toMinutes();

                if (duree2 != 90) {
                    continue;
                }

                boolean disponible1 =
                        disponibiliteRepository
                                .existsByProfesseurIdAndCreneauId(
                                        professeurId,
                                        premier.getId()
                                );

                boolean disponible2 =
                        disponibiliteRepository
                                .existsByProfesseurIdAndCreneauId(
                                        professeurId,
                                        deuxieme.getId()
                                );

                if (disponible1 && disponible2) {

                    List<CreneauHoraire> paire = new ArrayList<>();

                    paire.add(premier);
                    paire.add(deuxieme);

                    paires.add(paire);
                }
            }
        }

        return paires;
    }


    // =========================================================
    // 6. RECHERCHE DES SALLES
    // =========================================================

    private List<Salle> trouverSallesCompatibles(
            Groupe groupe,
            TypeSeance type
    ) {

        if (groupe.getEffectif() == null
                || groupe.getEffectif() <= 0) {

            throw new RuntimeException(
                    "Effectif invalide pour le groupe : "
                            + groupe.getNom()
            );
        }

        List<Salle> salles =
                salleRepository.findByCapaciteGreaterThanEqual(
                        groupe.getEffectif()
                );

        if (type == TypeSeance.TP) {

            salles = salles.stream()
                    .filter(salle ->
                            salle.getType() == TypeSalle.TP
                    )
                    .toList();
        }

        return salles.stream()
                .sorted(Comparator.comparing(Salle::getCapacite))
                .toList();
    }


    // =========================================================
    // 7. VÉRIFICATION ET RÉSERVATION DES CRÉNEAUX
    // =========================================================

    private boolean estCreneauLibre(
            Long professeurId,
            Long groupeId,
            Long salleId,
            Long creneauId,
            OccupationPlanning occupation
    ) {

        String cleProfesseur = professeurId + "-" + creneauId;
        String cleGroupe = groupeId + "-" + creneauId;
        String cleSalle = salleId + "-" + creneauId;

        if (occupation.professeurs.contains(cleProfesseur)) {
            return false;
        }

        if (occupation.groupes.contains(cleGroupe)) {
            return false;
        }

        if (occupation.salles.contains(cleSalle)) {
            return false;
        }

        return true;
    }


    private void reserverCreneau(
            Long professeurId,
            Long groupeId,
            Long salleId,
            Long creneauId,
            OccupationPlanning occupation
    ) {

        occupation.professeurs.add(
                professeurId + "-" + creneauId
        );

        occupation.groupes.add(
                groupeId + "-" + creneauId
        );

        occupation.salles.add(
                salleId + "-" + creneauId
        );
    }


    // =========================================================
    // 8. PLACEMENT DES COURS
    // =========================================================

    private boolean placerCours(
            SeanceAPlacer aPlacer,
            List<CreneauHoraire> creneaux,
            OccupationPlanning occupation,
            EmploiDuTemps emploi
    ) {

        Long professeurId =
                aPlacer.enseignement.getProfesseur().getId();

        Long groupeId = aPlacer.groupe.getId();

        Long matiereId =
                aPlacer.enseignement.getMatiere().getId();

        List<CreneauHoraire> creneauxTries =
                new ArrayList<>(creneaux);

        creneauxTries.sort(
                Comparator
                        .comparingInt((CreneauHoraire creneau) ->
                                existeTPMatiereCeJour(
                                        occupation,
                                        groupeId,
                                        matiereId,
                                        creneau
                                ) ? 1 : 0
                        )
                        .thenComparingInt(creneau ->
                                getChargeJour(
                                        occupation,
                                        groupeId,
                                        creneau
                                )
                        )
                        .thenComparing(
                                CreneauHoraire::getHeureDebut
                        )
        );

        for (CreneauHoraire creneau : creneauxTries) {

            long duree = Duration.between(
                    creneau.getHeureDebut(),
                    creneau.getHeureFin()
            ).toMinutes();

            if (duree != 90) {
                continue;
            }

            boolean disponible =
                    disponibiliteRepository
                            .existsByProfesseurIdAndCreneauId(
                                    professeurId,
                                    creneau.getId()
                            );

            if (!disponible) {
                continue;
            }

            List<Salle> salles = trouverSallesCompatibles(
                    aPlacer.groupe,
                    TypeSeance.COURS
            );

            for (Salle salle : salles) {

                boolean libre = estCreneauLibre(
                        professeurId,
                        groupeId,
                        salle.getId(),
                        creneau.getId(),
                        occupation
                );

                if (!libre) {
                    continue;
                }

                Seance seance = new Seance();

                seance.setDate(null);
                seance.setStatut(StatutSeance.PLANIFIEE);
                seance.setType(TypeSeance.COURS);
                seance.setProfesseur(
                        aPlacer.enseignement.getProfesseur()
                );
                seance.setGroupe(aPlacer.groupe);
                seance.setMatiere(
                        aPlacer.enseignement.getMatiere()
                );
                seance.setSalle(salle);
                seance.setCreneau(creneau);
                seance.setEmploiDuTemps(emploi);

                seanceRepository.save(seance);

                reserverCreneau(
                        professeurId,
                        groupeId,
                        salle.getId(),
                        creneau.getId(),
                        occupation
                );

                enregistrerCours(
                        occupation,
                        groupeId,
                        matiereId,
                        creneau
                );

                return true;
            }
        }

        return false;
    }


    private boolean existeTPMatiereCeJour(
            OccupationPlanning occupation,
            Long groupeId,
            Long matiereId,
            CreneauHoraire creneau
    ) {

        String key = cleMatiereJour(
                groupeId,
                matiereId,
                creneau
        );

        return occupation.matieresTPParJour.contains(key);
    }


    // =========================================================
    // 9. PLACEMENT DES TP
    // =========================================================

    private boolean placerTP(
            SeanceAPlacer aPlacer,
            List<CreneauHoraire> creneaux,
            OccupationPlanning occupation,
            EmploiDuTemps emploi
    ) {

        Long professeurId =
                aPlacer.enseignement.getProfesseur().getId();

        Long groupeId = aPlacer.groupe.getId();

        Long matiereId =
                aPlacer.enseignement.getMatiere().getId();

        List<List<CreneauHoraire>> paires =
                trouverPairesCreneauxTP(
                        professeurId,
                        creneaux
                );

        paires.sort(
                Comparator
                        .comparingInt((List<CreneauHoraire> paire) ->
                                existeCoursMatiereCeJour(
                                        occupation,
                                        groupeId,
                                        matiereId,
                                        paire.get(0)
                                ) ? 1 : 0
                        )
                        .thenComparingInt(paire ->
                                getChargeJour(
                                        occupation,
                                        groupeId,
                                        paire.get(0)
                                )
                        )
                        .thenComparingInt(paire ->
                                getNombreTPJour(
                                        occupation,
                                        groupeId,
                                        paire.get(0)
                                )
                        )
        );

        for (List<CreneauHoraire> paire : paires) {

            CreneauHoraire premier = paire.get(0);
            CreneauHoraire deuxieme = paire.get(1);

            // Maximum 2 blocs TP par groupe et par jour
            if (getNombreTPJour(
                    occupation,
                    groupeId,
                    premier
            ) >= 2) {
                continue;
            }

            List<Salle> salles = trouverSallesCompatibles(
                    aPlacer.groupe,
                    TypeSeance.TP
            );

            for (Salle salle : salles) {

                boolean premierLibre = estCreneauLibre(
                        professeurId,
                        groupeId,
                        salle.getId(),
                        premier.getId(),
                        occupation
                );

                boolean deuxiemeLibre = estCreneauLibre(
                        professeurId,
                        groupeId,
                        salle.getId(),
                        deuxieme.getId(),
                        occupation
                );

                if (!premierLibre || !deuxiemeLibre) {
                    continue;
                }

                Seance seance1 = new Seance();

                seance1.setDate(null);
                seance1.setStatut(StatutSeance.PLANIFIEE);
                seance1.setType(TypeSeance.TP);
                seance1.setProfesseur(
                        aPlacer.enseignement.getProfesseur()
                );
                seance1.setGroupe(aPlacer.groupe);
                seance1.setMatiere(
                        aPlacer.enseignement.getMatiere()
                );
                seance1.setSalle(salle);
                seance1.setCreneau(premier);
                seance1.setEmploiDuTemps(emploi);

                Seance seance2 = new Seance();

                seance2.setDate(null);
                seance2.setStatut(StatutSeance.PLANIFIEE);
                seance2.setType(TypeSeance.TP);
                seance2.setProfesseur(
                        aPlacer.enseignement.getProfesseur()
                );
                seance2.setGroupe(aPlacer.groupe);
                seance2.setMatiere(
                        aPlacer.enseignement.getMatiere()
                );
                seance2.setSalle(salle);
                seance2.setCreneau(deuxieme);
                seance2.setEmploiDuTemps(emploi);

                seanceRepository.save(seance1);
                seanceRepository.save(seance2);

                reserverCreneau(
                        professeurId,
                        groupeId,
                        salle.getId(),
                        premier.getId(),
                        occupation
                );

                reserverCreneau(
                        professeurId,
                        groupeId,
                        salle.getId(),
                        deuxieme.getId(),
                        occupation
                );

                enregistrerTP(
                        occupation,
                        groupeId,
                        matiereId,
                        premier,
                        deuxieme
                );

                return true;
            }
        }

        return false;
    }


    private boolean existeCoursMatiereCeJour(
            OccupationPlanning occupation,
            Long groupeId,
            Long matiereId,
            CreneauHoraire creneau
    ) {

        String key = cleMatiereJour(
                groupeId,
                matiereId,
                creneau
        );

        return occupation.matieresCoursParJour.contains(key);
    }


    // =========================================================
    // 10. MÉTHODE COMMUNE DE CRÉATION DE L'EMPLOI DU TEMPS
    // =========================================================

    /**
     * Construit le nouvel emploi du temps.
     *
     * Les anciennes séances ne sont supprimées qu'après
     * l'enregistrement réussi de toutes les nouvelles séances.
     *
     * Si une erreur survient, la transaction annule les changements.
     */
    private EmploiDuTemps construireEtEnregistrerEmploi(
            String semestre,
            Administrateur administrateur,
            List<EmploiDuTemps> anciensEmplois,
            List<SeanceAPlacer> seances
    ) {

        if (seances == null || seances.isEmpty()) {
            throw new RuntimeException(
                    "Aucune séance à générer"
            );
        }

        List<CreneauHoraire> creneaux =
                creneauHoraireRepository.findAll();

        if (creneaux.isEmpty()) {
            throw new RuntimeException(
                    "Aucun créneau horaire disponible"
            );
        }

        EmploiDuTemps emploi = new EmploiDuTemps();

        emploi.setSemestre(semestre);
        emploi.setAdministrateur(administrateur);

        emploi = emploiDuTempsRepository.save(emploi);

        OccupationPlanning occupation = new OccupationPlanning();

        List<SeanceAPlacer> cours = new ArrayList<>();
        List<SeanceAPlacer> tp = new ArrayList<>();

        List<SeanceAPlacer> seancesOrdonnees =
                new ArrayList<>();

        for (SeanceAPlacer seance : seances) {

            if (seance.type == TypeSeance.TP) {
                tp.add(seance);
            } else {
                cours.add(seance);
            }
        }

        // Alterner cours et TP
        int max = Math.max(cours.size(), tp.size());

        for (int i = 0; i < max; i++) {

            if (i < cours.size()) {
                seancesOrdonnees.add(cours.get(i));
            }

            if (i < tp.size()) {
                seancesOrdonnees.add(tp.get(i));
            }
        }

        // Placer toutes les séances
        for (SeanceAPlacer seance : seancesOrdonnees) {

            boolean placee;

            if (seance.type == TypeSeance.TP) {

                placee = placerTP(
                        seance,
                        creneaux,
                        occupation,
                        emploi
                );

            } else {

                placee = placerCours(
                        seance,
                        creneaux,
                        occupation,
                        emploi
                );
            }

            if (!placee) {

                throw new RuntimeException(
                        "Impossible de placer "
                                + seance.type
                                + " pour la matière "
                                + seance.enseignement
                                .getMatiere().getNom()
                                + " et le groupe "
                                + seance.groupe.getNom()
                );
            }
        }

        /*
         * Forcer l'enregistrement des nouvelles séances
         * avant de supprimer les anciens emplois du temps.
         */
        seanceRepository.flush();

        /*
         * Supprimer les anciens emplois seulement lorsque
         * toutes les nouvelles séances ont été placées.
         */
        if (anciensEmplois != null
                && !anciensEmplois.isEmpty()) {

            emploiDuTempsRepository.deleteAll(
                    anciensEmplois
            );

            emploiDuTempsRepository.flush();
        }

        return emploi;
    }


    // =========================================================
    // 11. GÉNÉRATION INITIALE
    // =========================================================

    /**
     * Génération initiale demandée par l'administrateur.
     * Cette méthode peut créer les affectations automatiques.
     */
    public EmploiDuTemps genererEmploiDuTemps(
            Long filiereId,
            String semestre,
            Long administrateurId
    ) {

        if (semestre == null || semestre.isBlank()) {
            throw new RuntimeException(
                    "Le semestre est obligatoire"
            );
        }

        verifierDonneesGeneration(
                filiereId,
                semestre
        );

        List<EmploiDuTemps> anciensEmplois =
                seanceRepository.findTimetablesByFiliereAndSemestre(
                        filiereId,
                        semestre
                );

        Administrateur administrateur =
                administrateurRepository.findById(administrateurId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Administrateur introuvable"
                                )
                        );

        // Génération initiale des affectations
        List<SeanceAPlacer> seances =
                preparerSeances(
                        filiereId,
                        semestre
                );

        return construireEtEnregistrerEmploi(
                semestre,
                administrateur,
                anciensEmplois,
                seances
        );
    }


    // =========================================================
    // 12. RÉGÉNÉRATION AVEC LES AFFECTATIONS EXISTANTES
    // =========================================================

    /**
     * Régénère un emploi du temps sans modifier les affectations.
     */
    public EmploiDuTemps genererAvecAffectationsExistantes(
            Long filiereId,
            String semestre,
            Long administrateurId
    ) {

        if (semestre == null || semestre.isBlank()) {
            throw new RuntimeException(
                    "Le semestre est obligatoire"
            );
        }

        verifierDonneesGeneration(
                filiereId,
                semestre
        );

        List<EmploiDuTemps> anciensEmplois =
                seanceRepository.findTimetablesByFiliereAndSemestre(
                        filiereId,
                        semestre
                );

        Administrateur administrateur =
                administrateurRepository.findById(administrateurId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Administrateur introuvable"
                                )
                        );

        // Récupérer les affectations déjà existantes
        // Aucune réaffectation des professeurs
        List<SeanceAPlacer> seances =
                preparerSeancesExistantes(
                        filiereId,
                        semestre
                );

        return construireEtEnregistrerEmploi(
                semestre,
                administrateur,
                anciensEmplois,
                seances
        );
    }


    // =========================================================
    // 13. RÉCUPÉRATION DES SÉANCES D'UN EMPLOI DU TEMPS
    // =========================================================

    @Transactional(readOnly = true)
    public List<SeanceResponse> getSeances(
            Long emploiId
    ) {

        return seanceRepository.findByEmploiDuTempsId(emploiId)
                .stream()
                .map(s -> new SeanceResponse(
                        s.getId(),
                        s.getDate(),

                        s.getCreneau().getJour().name(),
                        s.getCreneau().getHeureDebut().toString(),
                        s.getCreneau().getHeureFin().toString(),

                        s.getType().name(),
                        s.getStatut().name(),

                        s.getMatiere().getNom(),

                        s.getProfesseur().getPrenom()
                                + " "
                                + s.getProfesseur().getNom(),

                        s.getSalle().getNom(),
                        s.getGroupe().getNom()
                ))
                .toList();
    }


    // =========================================================
    // 14. RÉGÉNÉRATION APRÈS MODIFICATION DES DISPONIBILITÉS
    // =========================================================

    /**
     * Régénère les emplois du temps dans lesquels le professeur
     * possède déjà des séances.
     *
     * Les affectations automatiques restent inchangées.
     */
    public void regenererEmploisDuTempsProfesseur(
            Long professeurId
    ) {

        List<Seance> seancesExistantes =
                seanceRepository.findByProfesseurId(
                        professeurId
                );

        if (seancesExistantes.isEmpty()) {
            return;
        }

        // Une seule régénération par filière et semestre
        Map<String, CibleRegeneration> emploisARegenerer =
                new LinkedHashMap<>();

        for (Seance seance : seancesExistantes) {

            EmploiDuTemps emploi =
                    seance.getEmploiDuTemps();

            Groupe groupe =
                    seance.getGroupe();

            if (emploi == null
                    || groupe == null
                    || groupe.getFiliere() == null
                    || emploi.getAdministrateur() == null
                    || emploi.getSemestre() == null) {

                continue;
            }

            Long filiereId =
                    groupe.getFiliere().getId();

            String semestre =
                    emploi.getSemestre();

            Long administrateurId =
                    emploi.getAdministrateur().getId();

            String cle =
                    filiereId + "-" + semestre;

            emploisARegenerer.putIfAbsent(
                    cle,
                    new CibleRegeneration(
                            filiereId,
                            semestre,
                            administrateurId
                    )
            );
        }

        // Régénérer chaque filière/semestre concerné
        for (CibleRegeneration cible :
                emploisARegenerer.values()) {

            genererAvecAffectationsExistantes(
                    cible.filiereId(),
                    cible.semestre(),
                    cible.administrateurId()
            );
        }
    }


    // Informations nécessaires pour une régénération
    private record CibleRegeneration(
            Long filiereId,
            String semestre,
            Long administrateurId
    ) {
    }
}
