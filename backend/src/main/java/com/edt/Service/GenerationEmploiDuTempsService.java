
package com.edt.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.edt.dto.SeanceResponse;
import com.edt.entity.*;
import com.edt.entity.enums.StatutSeance;
import com.edt.entity.enums.TypeSalle;
import com.edt.entity.enums.TypeSeance;

import com.edt.repository.AdministrateurRepository;
import com.edt.repository.CreneauHoraireRepository;
import com.edt.repository.DisponibiliteRepository;
import com.edt.repository.EmploiDuTempsRepository;
import com.edt.repository.EnseignementRepository;
import com.edt.repository.FiliereRepository;
import com.edt.repository.GroupeRepository;
import com.edt.repository.SalleRepository;
import com.edt.repository.SeanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GenerationEmploiDuTempsService {

    private static final long DUREE_CRENEAU_MINUTES = 90;

    // Limite de la recherche pour éviter une exécution infinie.
    private static final int MAX_NOEUDS_RECHERCHE = 200_000;

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

        private final Enseignement enseignement;
        private final Groupe groupe;
        private final TypeSeance type;

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

    /** Un choix possible : créneau(x) + salle pour une séance. */
    private static class Affectation {

        private final SeanceAPlacer seance;
        private final List<CreneauHoraire> creneaux;
        private final Salle salle;

        private Affectation(
                SeanceAPlacer seance,
                List<CreneauHoraire> creneaux,
                Salle salle
        ) {
            this.seance = seance;
            this.creneaux = creneaux;
            this.salle = salle;
        }
    }

    private static class OccupationPlanning {

        private final Set<String> professeurs = new HashSet<>();
        private final Set<String> groupes = new HashSet<>();
        private final Set<String> salles = new HashSet<>();

        // Créneaux de 90 min occupés par groupe et par jour.
        private final Map<String, Integer> chargeGroupeParJour = new HashMap<>();

        // Compteurs pour pouvoir annuler un choix pendant la recherche.
        private final Map<String, Integer> coursParJour = new HashMap<>();
        private final Map<String, Integer> tpParJour = new HashMap<>();

        // Caches.
        private final Map<Long, Set<Long>> disponibilitesProf = new HashMap<>();
        private final Map<String, List<Salle>> sallesCompatibles = new HashMap<>();
        private final Map<Long, List<List<CreneauHoraire>>> pairesProf = new HashMap<>();
        private final Map<Long, List<List<CreneauHoraire>>> coursProf = new HashMap<>();
    }

    /** Résultat partagé pendant la recherche. */
    private static class Recherche {
        private int noeuds = 0;
        private int profondeurMax = -1;
        private String diagnostic = "aucune solution trouvée";
        private boolean limiteAtteinte = false;
    }

    // =========================================================
    // 2. CLÉS ET STATISTIQUES
    // =========================================================

    private String cleGroupeJour(Long groupeId, CreneauHoraire creneau) {
        return groupeId + "-" + creneau.getJour().name();
    }

    private String cleMatiereJour(
            Long groupeId,
            Long matiereId,
            CreneauHoraire creneau
    ) {
        return groupeId + "-" + matiereId + "-" + creneau.getJour().name();
    }

    private int getChargeJour(
            OccupationPlanning occupation,
            Long groupeId,
            CreneauHoraire creneau
    ) {
        return occupation.chargeGroupeParJour
                .getOrDefault(cleGroupeJour(groupeId, creneau), 0);
    }

    private void ajuster(Map<String, Integer> map, String cle, int delta) {
        int valeur = map.getOrDefault(cle, 0) + delta;

        if (valeur <= 0) {
            map.remove(cle);
        } else {
            map.put(cle, valeur);
        }
    }

    // =========================================================
    // 3. VALIDATION DES DONNÉES
    // =========================================================

    private void verifierDonneesGeneration(Long filiereId, String semestre) {

        var filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new RuntimeException("Filière introuvable"));

        if (groupeRepository
                .findByFiliereIdAndSemestre(filiere.getId(), semestre)
                .isEmpty()) {
            throw new RuntimeException(
                    "Aucun groupe trouvé pour cette filière et ce semestre");
        }

        if (creneauHoraireRepository.count() == 0) {
            throw new RuntimeException("Aucun créneau horaire enregistré");
        }

        if (salleRepository.count() == 0) {
            throw new RuntimeException("Aucune salle enregistrée");
        }
    }

    /** Un nombre d'heures null vaut 0 séance. */
    private int calculerNombreSeances(
            Float heures,
            float dureeSeance,
            String typeSeance
    ) {
        if (heures == null) {
            return 0;
        }

        if (heures < 0) {
            throw new RuntimeException(
                    "Nombre d'heures invalide pour " + typeSeance);
        }

        int nombre = Math.round(heures / dureeSeance);

        if (Math.abs(heures - (nombre * dureeSeance)) > 0.001f) {
            throw new RuntimeException(
                    "Les heures de " + typeSeance
                            + " doivent être un multiple de " + dureeSeance);
        }

        return nombre;
    }

    // =========================================================
    // 4. PRÉPARATION DES SÉANCES
    // =========================================================

    private List<SeanceAPlacer> preparerSeances(Long filiereId, String semestre) {
        return construireSeances(
                enseignementService.genererAffectationsAutomatiques(
                        filiereId, semestre));
    }

    private List<SeanceAPlacer> preparerSeancesExistantes(
            Long filiereId,
            String semestre
    ) {
        return construireSeances(
                enseignementRepository
                        .findAffectationsAutomatiquesByFiliereAndSemestre(
                                filiereId, semestre));
    }

    private List<SeanceAPlacer> construireSeances(
            List<Enseignement> enseignements
    ) {
        if (enseignements == null || enseignements.isEmpty()) {
            throw new RuntimeException(
                    "Aucune affectation automatique trouvée pour cette filière et ce semestre.");
        }

        List<SeanceAPlacer> seances = new ArrayList<>();

        for (Enseignement enseignement : enseignements) {

            Groupe groupe = enseignement.getGroupe();

            if (groupe == null) {
                throw new RuntimeException(
                        "Aucun groupe associé à l'affectation "
                                + enseignement.getId());
            }

            if (enseignement.getProfesseur() == null) {
                throw new RuntimeException(
                        "Aucun professeur associé à l'affectation "
                                + enseignement.getId());
            }

            int nombreCours = calculerNombreSeances(
                    enseignement.getHeuresCours(), 1.5f, "COURS");

            int nombreTP = calculerNombreSeances(
                    enseignement.getHeuresTP(), 3f, "TP");

            for (int i = 0; i < nombreCours; i++) {
                seances.add(new SeanceAPlacer(enseignement, groupe, TypeSeance.COURS));
            }

            for (int i = 0; i < nombreTP; i++) {
                seances.add(new SeanceAPlacer(enseignement, groupe, TypeSeance.TP));
            }
        }

        return seances;
    }

    // =========================================================
    // 5. DISPONIBILITÉS, CRÉNEAUX ET SALLES
    // =========================================================

    private boolean estCreneau90Min(CreneauHoraire creneau) {
        return Duration.between(creneau.getHeureDebut(), creneau.getHeureFin())
                .toMinutes() == DUREE_CRENEAU_MINUTES;
    }

    private boolean estProfesseurDisponible(
            Long professeurId,
            CreneauHoraire creneau,
            List<CreneauHoraire> tousCreneaux,
            OccupationPlanning occupation
    ) {
        Set<Long> dispos = occupation.disponibilitesProf.computeIfAbsent(
                professeurId,
                id -> {
                    Set<Long> ids = new HashSet<>();
                    for (CreneauHoraire c : tousCreneaux) {
                        if (disponibiliteRepository
                                .existsByProfesseurIdAndCreneauId(id, c.getId())) {
                            ids.add(c.getId());
                        }
                    }
                    return ids;
                });

        return dispos.contains(creneau.getId());
    }

    private List<List<CreneauHoraire>> calculerPairesTP(
            Long professeurId,
            List<CreneauHoraire> creneaux,
            OccupationPlanning occupation
    ) {
        List<List<CreneauHoraire>> paires = new ArrayList<>();

        List<CreneauHoraire> tries = new ArrayList<>(creneaux);
        tries.sort(Comparator.comparing(CreneauHoraire::getJour)
                .thenComparing(CreneauHoraire::getHeureDebut));

        for (int i = 0; i < tries.size(); i++) {

            CreneauHoraire premier = tries.get(i);

            if (!estCreneau90Min(premier)) {
                continue;
            }

            for (int j = i + 1; j < tries.size(); j++) {

                CreneauHoraire deuxieme = tries.get(j);

                if (premier.getJour() != deuxieme.getJour()) {
                    break;
                }

                // Écart obligatoire de 5 minutes.
                if (!premier.getHeureFin().plusMinutes(5)
                        .equals(deuxieme.getHeureDebut())) {
                    continue;
                }

                if (!estCreneau90Min(deuxieme)) {
                    continue;
                }

                if (estProfesseurDisponible(professeurId, premier, creneaux, occupation)
                        && estProfesseurDisponible(professeurId, deuxieme, creneaux, occupation)) {

                    List<CreneauHoraire> paire = new ArrayList<>();
                    paire.add(premier);
                    paire.add(deuxieme);
                    paires.add(paire);
                }
            }
        }

        return paires;
    }

    /** Unités de placement possibles pour le professeur. */
    private List<List<CreneauHoraire>> unitesBase(
            SeanceAPlacer s,
            List<CreneauHoraire> creneaux,
            OccupationPlanning occupation
    ) {
        Long profId = s.enseignement.getProfesseur().getId();

        if (s.type == TypeSeance.TP) {
            return occupation.pairesProf.computeIfAbsent(
                    profId, id -> calculerPairesTP(id, creneaux, occupation));
        }

        return occupation.coursProf.computeIfAbsent(profId, id -> {
            List<List<CreneauHoraire>> unites = new ArrayList<>();

            for (CreneauHoraire c : creneaux) {
                if (estCreneau90Min(c)
                        && estProfesseurDisponible(id, c, creneaux, occupation)) {
                    unites.add(List.of(c));
                }
            }

            return unites;
        });
    }

    private List<Salle> trouverSallesCompatibles(
            Groupe groupe,
            TypeSeance type,
            OccupationPlanning occupation
    ) {
        String cache = groupe.getId() + "-" + type;

        List<Salle> cachees = occupation.sallesCompatibles.get(cache);
        if (cachees != null) {
            return cachees;
        }

        if (groupe.getEffectif() == null || groupe.getEffectif() <= 0) {
            throw new RuntimeException(
                    "Effectif invalide pour le groupe : " + groupe.getNom());
        }

        List<Salle> salles = salleRepository
                .findByCapaciteGreaterThanEqual(groupe.getEffectif());

        List<Salle> resultat;

        if (type == TypeSeance.TP) {
            resultat = salles.stream()
                    .filter(s -> s.getType() == TypeSalle.TP)
                    .sorted(Comparator.comparing(Salle::getCapacite))
                    .toList();
        } else {
            // Les cours évitent les salles de TP tant que possible.
            resultat = salles.stream()
                    .sorted(Comparator
                            .comparingInt((Salle s) ->
                                    s.getType() == TypeSalle.TP ? 1 : 0)
                            .thenComparing(Salle::getCapacite))
                    .toList();
        }

        occupation.sallesCompatibles.put(cache, resultat);
        return resultat;
    }

    // =========================================================
    // 6. OCCUPATION : VÉRIFIER, APPLIQUER, ANNULER
    // =========================================================

    private boolean estCreneauLibre(
            Long professeurId,
            Long groupeId,
            Long salleId,
            Long creneauId,
            OccupationPlanning occupation
    ) {
        return !occupation.professeurs.contains(professeurId + "-" + creneauId)
                && !occupation.groupes.contains(groupeId + "-" + creneauId)
                && !occupation.salles.contains(salleId + "-" + creneauId);
    }

    private void appliquer(Affectation a, OccupationPlanning occ) {

        Long profId = a.seance.enseignement.getProfesseur().getId();
        Long groupeId = a.seance.groupe.getId();
        Long matiereId = a.seance.enseignement.getMatiere().getId();
        CreneauHoraire premier = a.creneaux.get(0);

        for (CreneauHoraire c : a.creneaux) {
            occ.professeurs.add(profId + "-" + c.getId());
            occ.groupes.add(groupeId + "-" + c.getId());
            occ.salles.add(a.salle.getId() + "-" + c.getId());
        }

        String matiereKey = cleMatiereJour(groupeId, matiereId, premier);

        ajuster(occ.chargeGroupeParJour,
                cleGroupeJour(groupeId, premier), a.creneaux.size());

        ajuster(a.seance.type == TypeSeance.TP
                ? occ.tpParJour : occ.coursParJour, matiereKey, 1);
    }

    private void annuler(Affectation a, OccupationPlanning occ) {

        Long profId = a.seance.enseignement.getProfesseur().getId();
        Long groupeId = a.seance.groupe.getId();
        Long matiereId = a.seance.enseignement.getMatiere().getId();
        CreneauHoraire premier = a.creneaux.get(0);

        for (CreneauHoraire c : a.creneaux) {
            occ.professeurs.remove(profId + "-" + c.getId());
            occ.groupes.remove(groupeId + "-" + c.getId());
            occ.salles.remove(a.salle.getId() + "-" + c.getId());
        }

        String matiereKey = cleMatiereJour(groupeId, matiereId, premier);

        ajuster(occ.chargeGroupeParJour,
                cleGroupeJour(groupeId, premier), -a.creneaux.size());

        ajuster(a.seance.type == TypeSeance.TP
                ? occ.tpParJour : occ.coursParJour, matiereKey, -1);
    }

    /**
     * Pré-remplit l'occupation avec les séances des AUTRES emplois du temps
     * (professeurs / salles partagés). Les emplois remplacés sont ignorés.
     */
    private void chargerOccupationExistante(
            OccupationPlanning occupation,
            EmploiDuTemps nouvelEmploi,
            List<EmploiDuTemps> anciensEmplois
    ) {
        Set<Long> emploisIgnores = new HashSet<>();
        emploisIgnores.add(nouvelEmploi.getId());

        if (anciensEmplois != null) {
            for (EmploiDuTemps ancien : anciensEmplois) {
                emploisIgnores.add(ancien.getId());
            }
        }

        for (Seance s : seanceRepository.findAll()) {

            if (s.getEmploiDuTemps() == null
                    || emploisIgnores.contains(s.getEmploiDuTemps().getId())
                    || s.getCreneau() == null) {
                continue;
            }

            Long creneauId = s.getCreneau().getId();

            if (s.getProfesseur() != null) {
                occupation.professeurs.add(s.getProfesseur().getId() + "-" + creneauId);
            }
            if (s.getGroupe() != null) {
                occupation.groupes.add(s.getGroupe().getId() + "-" + creneauId);
            }
            if (s.getSalle() != null) {
                occupation.salles.add(s.getSalle().getId() + "-" + creneauId);
            }
        }
    }

    // =========================================================
    // 7. CANDIDATS POUR UNE SÉANCE
    // =========================================================

    private List<Affectation> candidats(
            SeanceAPlacer s,
            List<CreneauHoraire> creneaux,
            OccupationPlanning occ
    ) {
        Long profId = s.enseignement.getProfesseur().getId();
        Long groupeId = s.groupe.getId();
        Long matiereId = s.enseignement.getMatiere().getId();

        List<Salle> salles = trouverSallesCompatibles(s.groupe, s.type, occ);
        List<Affectation> resultat = new ArrayList<>();

        for (List<CreneauHoraire> unite : unitesBase(s, creneaux, occ)) {

            // Aucune limite du nombre de séances de la matière par jour.

            boolean libre = unite.stream().allMatch(c ->
                    !occ.professeurs.contains(profId + "-" + c.getId())
                            && !occ.groupes.contains(groupeId + "-" + c.getId()));

            if (!libre) {
                continue;
            }

            for (Salle salle : salles) {

                boolean salleLibre = unite.stream().allMatch(c ->
                        estCreneauLibre(profId, groupeId, salle.getId(), c.getId(), occ));

                if (salleLibre) {
                    resultat.add(new Affectation(s, unite, salle));
                    break;
                }
            }
        }

        // Préférence : éviter un cours et un TP de la même matière le même jour,
        // équilibrer la charge du groupe, puis ordre chronologique.
        resultat.sort(
                Comparator
                        .comparingInt((Affectation a) -> {
                            String cle = cleMatiereJour(groupeId, matiereId, a.creneaux.get(0));
                            Map<String, Integer> autre = s.type == TypeSeance.TP
                                    ? occ.coursParJour : occ.tpParJour;
                            return autre.containsKey(cle) ? 1 : 0;
                        })
                        .thenComparingInt(a ->
                                getChargeJour(occ, groupeId, a.creneaux.get(0)))
                        .thenComparing(a -> a.creneaux.get(0).getJour())
                        .thenComparing(a -> a.creneaux.get(0).getHeureDebut())
        );

        return resultat;
    }

    // =========================================================
    // 8. RECHERCHE AVEC RETOUR ARRIÈRE
    // =========================================================

    /**
     * Place toutes les séances. À chaque étape on traite la séance la plus
     * contrainte (le moins de candidats) ; en cas d'impasse on annule le
     * dernier choix et on essaie le suivant.
     */
    private boolean resoudre(
            List<SeanceAPlacer> restantes,
            List<Affectation> solution,
            List<CreneauHoraire> creneaux,
            OccupationPlanning occ,
            Recherche recherche
    ) {
        if (restantes.isEmpty()) {
            return true;
        }

        if (++recherche.noeuds > MAX_NOEUDS_RECHERCHE) {
            recherche.limiteAtteinte = true;
            return false;
        }

        SeanceAPlacer choisie = null;
        List<Affectation> meilleurs = null;

        for (SeanceAPlacer s : restantes) {

            List<Affectation> cands = candidats(s, creneaux, occ);

            if (cands.isEmpty()) {
                // Impasse : on garde le diagnostic de l'impasse la plus profonde.
                if (solution.size() > recherche.profondeurMax) {
                    recherche.profondeurMax = solution.size();
                    recherche.diagnostic =
                            "Impossible de placer " + s.type
                                    + " pour la matière "
                                    + s.enseignement.getMatiere().getNom()
                                    + " et le groupe " + s.groupe.getNom()
                                    + " → " + diagnostiquerEchec(s, creneaux, occ);
                }
                return false;
            }

            if (meilleurs == null || cands.size() < meilleurs.size()) {
                choisie = s;
                meilleurs = cands;
            }
        }

        restantes.remove(choisie);

        for (Affectation a : meilleurs) {

            appliquer(a, occ);
            solution.add(a);

            if (resoudre(restantes, solution, creneaux, occ, recherche)) {
                return true;
            }

            solution.remove(solution.size() - 1);
            annuler(a, occ);

            if (recherche.limiteAtteinte) {
                break;
            }
        }

        restantes.add(choisie);
        return false;
    }

    // =========================================================
    // 9. DIAGNOSTIC D'UNE IMPASSE
    // =========================================================

    private String diagnostiquerEchec(
            SeanceAPlacer s,
            List<CreneauHoraire> creneaux,
            OccupationPlanning occ
    ) {
        Long profId = s.enseignement.getProfesseur().getId();
        Long groupeId = s.groupe.getId();
        String prof = s.enseignement.getProfesseur().getNom();
        boolean tp = s.type == TypeSeance.TP;

        List<Salle> salles = trouverSallesCompatibles(s.groupe, s.type, occ);

        if (salles.isEmpty()) {
            return "aucune salle " + (tp ? "de type TP " : "")
                    + "avec une capacité >= " + s.groupe.getEffectif();
        }

        List<List<CreneauHoraire>> unites = unitesBase(s, creneaux, occ);

        if (unites.isEmpty()) {
            return "le professeur " + prof + " n'a aucune disponibilité"
                    + (tp ? " sur 2 créneaux de 90 min consécutifs avec 5 min d'écart"
                    + " (vérifiez les horaires des créneaux et ses disponibilités)"
                    : " sur un créneau de 90 min");
        }

        int profOccupe = 0;
        int groupeOccupe = 0;
        int salleOccupee = 0;

        for (List<CreneauHoraire> unite : unites) {

            if (unite.stream().anyMatch(c ->
                    occ.professeurs.contains(profId + "-" + c.getId()))) {
                profOccupe++;
            } else if (unite.stream().anyMatch(c ->
                    occ.groupes.contains(groupeId + "-" + c.getId()))) {
                groupeOccupe++;
            } else {
                salleOccupee++;
            }
        }

        return unites.size() + " option(s) possible(s) pour " + prof
                + " : " + profOccupe + " professeur déjà occupé, "
                + groupeOccupe + " groupe déjà occupé, "
                + salleOccupee + " sans salle libre (" + salles.size()
                + " salle(s) compatible(s))";
    }

    // =========================================================
    // 10. CRÉATION DE L'EMPLOI DU TEMPS
    // =========================================================

    private Seance creerSeance(
            Affectation a,
            CreneauHoraire creneau,
            EmploiDuTemps emploi
    ) {
        Seance seance = new Seance();

        seance.setDate(null);
        seance.setStatut(StatutSeance.PLANIFIEE);
        seance.setType(a.seance.type);
        seance.setProfesseur(a.seance.enseignement.getProfesseur());
        seance.setGroupe(a.seance.groupe);
        seance.setMatiere(a.seance.enseignement.getMatiere());
        seance.setSalle(a.salle);
        seance.setCreneau(creneau);
        seance.setEmploiDuTemps(emploi);

        return seance;
    }

    private EmploiDuTemps construireEtEnregistrerEmploi(
            String semestre,
            Administrateur administrateur,
            List<EmploiDuTemps> anciensEmplois,
            List<SeanceAPlacer> seances
    ) {
        if (seances == null || seances.isEmpty()) {
            throw new RuntimeException("Aucune séance à générer");
        }

        List<CreneauHoraire> creneaux = creneauHoraireRepository.findAll();

        if (creneaux.isEmpty()) {
            throw new RuntimeException("Aucun créneau horaire disponible");
        }

        EmploiDuTemps emploi = new EmploiDuTemps();
        emploi.setSemestre(semestre);
        emploi.setAdministrateur(administrateur);
        emploi = emploiDuTempsRepository.save(emploi);

        OccupationPlanning occupation = new OccupationPlanning();
        chargerOccupationExistante(occupation, emploi, anciensEmplois);

        // 1) Recherche de la solution complète EN MÉMOIRE.
        List<Affectation> solution = new ArrayList<>();
        Recherche recherche = new Recherche();

        boolean ok = resoudre(
                new ArrayList<>(seances),
                solution,
                creneaux,
                occupation,
                recherche
        );

        if (!ok) {
            throw new RuntimeException(
                    recherche.limiteAtteinte
                            ? "Limite de recherche atteinte. Dernière impasse : "
                            + recherche.diagnostic
                            : recherche.diagnostic
            );
        }

        // 2) Enregistrement uniquement si tout a pu être placé.
        for (Affectation a : solution) {
            for (CreneauHoraire creneau : a.creneaux) {
                seanceRepository.save(creerSeance(a, creneau, emploi));
            }
        }

        seanceRepository.flush();

        // 3) Suppression des anciens emplois après succès.
        if (anciensEmplois != null && !anciensEmplois.isEmpty()) {
            emploiDuTempsRepository.deleteAll(anciensEmplois);
            emploiDuTempsRepository.flush();
        }

        return emploi;
    }

    // =========================================================
    // 11. GÉNÉRATION INITIALE ET RÉGÉNÉRATION
    // =========================================================

    public EmploiDuTemps genererEmploiDuTemps(
            Long filiereId,
            String semestre,
            Long administrateurId
    ) {
        return generer(filiereId, semestre, administrateurId, true);
    }

    public EmploiDuTemps genererAvecAffectationsExistantes(
            Long filiereId,
            String semestre,
            Long administrateurId
    ) {
        return generer(filiereId, semestre, administrateurId, false);
    }

    private EmploiDuTemps generer(
            Long filiereId,
            String semestre,
            Long administrateurId,
            boolean nouvellesAffectations
    ) {
        if (semestre == null || semestre.isBlank()) {
            throw new RuntimeException("Le semestre est obligatoire");
        }

        verifierDonneesGeneration(filiereId, semestre);

        List<EmploiDuTemps> anciensEmplois =
                seanceRepository.findTimetablesByFiliereAndSemestre(
                        filiereId, semestre);

        Administrateur administrateur =
                administrateurRepository.findById(administrateurId)
                        .orElseThrow(() ->
                                new RuntimeException("Administrateur introuvable"));

        List<SeanceAPlacer> seances = nouvellesAffectations
                ? preparerSeances(filiereId, semestre)
                : preparerSeancesExistantes(filiereId, semestre);

        return construireEtEnregistrerEmploi(
                semestre, administrateur, anciensEmplois, seances);
    }

    // =========================================================
    // 12. RÉCUPÉRATION DES SÉANCES
    // =========================================================

    @Transactional(readOnly = true)
    public List<SeanceResponse> getSeances(Long emploiId) {

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
                        s.getProfesseur().getPrenom() + " " + s.getProfesseur().getNom(),
                        s.getSalle().getNom(),
                        s.getGroupe().getNom()
                ))
                .toList();
    }

    // =========================================================
    // 13. RÉGÉNÉRATION APRÈS MODIFICATION DES DISPONIBILITÉS
    // =========================================================

    public void regenererEmploisDuTempsProfesseur(Long professeurId) {

        List<Seance> seancesExistantes =
                seanceRepository.findByProfesseurId(professeurId);

        if (seancesExistantes.isEmpty()) {
            return;
        }

        Map<String, CibleRegeneration> cibles = new LinkedHashMap<>();

        for (Seance seance : seancesExistantes) {

            EmploiDuTemps emploi = seance.getEmploiDuTemps();
            Groupe groupe = seance.getGroupe();

            if (emploi == null
                    || groupe == null
                    || groupe.getFiliere() == null
                    || emploi.getAdministrateur() == null
                    || emploi.getSemestre() == null) {
                continue;
            }

            Long filiereId = groupe.getFiliere().getId();
            String semestre = emploi.getSemestre();

            cibles.putIfAbsent(
                    filiereId + "-" + semestre,
                    new CibleRegeneration(
                            filiereId,
                            semestre,
                            emploi.getAdministrateur().getId()));
        }

        for (CibleRegeneration cible : cibles.values()) {
            genererAvecAffectationsExistantes(
                    cible.filiereId(),
                    cible.semestre(),
                    cible.administrateurId());
        }
    }


    public void regenererEmploiDuTempsApresRefus(
            Long professeurId,
            Long groupeId
    ) {

        // Récupérer les séances actuelles du professeur.
        List<Seance> seancesExistantes =
                seanceRepository.findByProfesseurId(professeurId);

        Map<String, CibleRegeneration> cibles = new LinkedHashMap<>();

        // Identifier les emplois du temps concernés par ce groupe.
        for (Seance seance : seancesExistantes) {

            Groupe groupe = seance.getGroupe();
            EmploiDuTemps emploi = seance.getEmploiDuTemps();

            if (groupe == null
                    || !groupe.getId().equals(groupeId)
                    || emploi == null
                    || groupe.getFiliere() == null
                    || emploi.getAdministrateur() == null
                    || emploi.getSemestre() == null) {
                continue;
            }

            Long filiereId = groupe.getFiliere().getId();
            String semestre = emploi.getSemestre();

            cibles.putIfAbsent(
                    filiereId + "-" + semestre,
                    new CibleRegeneration(
                            filiereId,
                            semestre,
                            emploi.getAdministrateur().getId()
                    )
            );
        }

        if (cibles.isEmpty()) {
            throw new RuntimeException(
                    "Aucun emploi du temps trouvé pour ce professeur et ce groupe."
            );
        }

        // Régénérer avec de nouvelles affectations automatiques.
        for (CibleRegeneration cible : cibles.values()) {

            genererEmploiDuTemps(
                    cible.filiereId(),
                    cible.semestre(),
                    cible.administrateurId()
            );
        }
    }



    private record CibleRegeneration(
            Long filiereId,
            String semestre,
            Long administrateurId
    ) {
    }
}
