
package com.edt.service;

import com.edt.entity.*;
import com.edt.dto.EnseignementDTO;
import com.edt.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class EnseignementService {

    private static final long DUREE_CRENEAU_MINUTES = 90;

    private final EnseignementRepository enseignementRepository;
    private final ProfesseurRepository professeurRepository;
    private final MatiereRepository matiereRepository;
    private final FiliereRepository filiereRepository;

    private final FiliereMatiereRepository filiereMatiereRepository;
    private final PreferenceMatiereRepository preferenceMatiereRepository;
    private final GroupeRepository groupeRepository;

    private final DisponibiliteRepository disponibiliteRepository;

    // =====================================================
    // ENTITY TO DTO
    // =====================================================

    private EnseignementDTO convertirEnDTO(Enseignement e) {

        return EnseignementDTO.builder()
                .id(e.getId())
                .professeurId(e.getProfesseur().getId())
                .professeurNom(e.getProfesseur().getNom())
                .professeurPrenom(e.getProfesseur().getPrenom())
                .matiereId(e.getMatiere().getId())
                .matiereNom(e.getMatiere().getNom())
                .filiereId(e.getFiliere().getId())
                .filiereNom(e.getFiliere().getNom())
                .heuresCours(e.getHeuresCours())
                .heuresTP(e.getHeuresTP())
                .build();
    }

    // =====================================================
    // VÉRIFIER LES DISPONIBILITÉS
    // =====================================================

    private boolean possedeDisponibiliteCours(Long professeurId) {

        List<Disponibilite> disponibilites =
                disponibiliteRepository.findByProfesseurId(professeurId);

        return disponibilites.stream().anyMatch(d -> {
            CreneauHoraire c = d.getCreneau();

            return c != null
                    && Duration.between(
                    c.getHeureDebut(),
                    c.getHeureFin()
            ).toMinutes() == DUREE_CRENEAU_MINUTES;
        });
    }

    private boolean possedeDisponibiliteTP(Long professeurId) {

        List<Disponibilite> disponibilites =
                disponibiliteRepository.findByProfesseurId(professeurId);

        List<CreneauHoraire> creneaux = disponibilites.stream()
                .map(Disponibilite::getCreneau)
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparing(CreneauHoraire::getJour)
                                .thenComparing(CreneauHoraire::getHeureDebut)
                )
                .toList();

        for (int i = 0; i < creneaux.size(); i++) {

            CreneauHoraire premier = creneaux.get(i);

            if (Duration.between(
                    premier.getHeureDebut(),
                    premier.getHeureFin()
            ).toMinutes() != DUREE_CRENEAU_MINUTES) {
                continue;
            }

            for (int j = i + 1; j < creneaux.size(); j++) {

                CreneauHoraire deuxieme = creneaux.get(j);

                if (premier.getJour() != deuxieme.getJour()) {
                    break;
                }

                if (premier.getHeureFin().plusMinutes(5)
                        .equals(deuxieme.getHeureDebut())
                        && Duration.between(
                        deuxieme.getHeureDebut(),
                        deuxieme.getHeureFin()
                ).toMinutes() == DUREE_CRENEAU_MINUTES) {

                    return true;
                }
            }
        }

        return false;
    }

    // =====================================================
    // VÉRIFIER SI LE PROFESSEUR PEUT ASSURER TOUTE LA MATIÈRE
    // =====================================================

    private boolean estDisponiblePourAffectation(
            Professeur professeur,
            float heuresCours,
            float heuresTP
    ) {

        if (heuresCours > 0
                && !possedeDisponibiliteCours(professeur.getId())) {
            return false;
        }

        if (heuresTP > 0
                && !possedeDisponibiliteTP(professeur.getId())) {
            return false;
        }

        return true;
    }

    // =====================================================
    // CALCUL DES UNITÉS
    // =====================================================

    private int calculerUnites(
            float heures,
            float dureeUnite,
            String type
    ) {

        int nombre = Math.round(heures / dureeUnite);

        if (Math.abs(heures - nombre * dureeUnite) > 0.001f) {
            throw new RuntimeException(
                    "Les heures de " + type
                            + " doivent être un multiple de "
                            + dureeUnite + " heures"
            );
        }

        return nombre;
    }

    // =====================================================
    // AUTOMATIC PROFESSOR ALLOCATION
    // =====================================================

    public List<Enseignement> genererAffectationsAutomatiques(
            Long filiereId,
            String semestre
    ) {

        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() ->
                        new RuntimeException("Filière introuvable"));

        List<Groupe> groupes =
                groupeRepository.findByFiliereIdAndSemestre(
                        filiereId,
                        semestre
                );

        if (groupes.isEmpty()) {
            throw new RuntimeException(
                    "Aucun groupe trouvé pour cette filière"
            );
        }

        List<FiliereMatiere> matieres =
                filiereMatiereRepository.findByFiliereIdAndSemestre(
                        filiereId,
                        semestre
                );

        if (matieres.isEmpty()) {
            throw new RuntimeException(
                    "Aucune matière configurée pour cette filière"
            );
        }

        // Trier les groupes et les matières pour garder
        // une répartition stable entre les générations.

        List<Groupe> groupesTries = groupes.stream()
                .sorted(Comparator.comparing(Groupe::getId))
                .toList();

        List<FiliereMatiere> matieresTriees = matieres.stream()
                .sorted(Comparator.comparing(
                        fm -> fm.getMatiere().getId()
                ))
                .toList();

        // =================================================
        // 1. RÉCUPÉRER LES PROFESSEURS PAR MATIÈRE
        // =================================================

        Map<Long, List<Professeur>> professeursParMatiere =
                new HashMap<>();

        for (FiliereMatiere fm : matieresTriees) {

            Long matiereId = fm.getMatiere().getId();

            List<PreferenceMatiere> preferences =
                    preferenceMatiereRepository.findByMatiereId(matiereId);

            Map<Long, Professeur> candidatsUniques =
                    new LinkedHashMap<>();

            for (PreferenceMatiere preference : preferences) {

                Professeur professeur = preference.getProfesseur();

                if (professeur == null || professeur.getId() == null) {
                    continue;
                }

                candidatsUniques.put(
                        professeur.getId(),
                        professeur
                );
            }

            List<Professeur> candidats =
                    new ArrayList<>(candidatsUniques.values());

            float heuresCours = fm.getHeuresCours() == null
                    ? 0 : fm.getHeuresCours();

            float heuresTP = fm.getHeuresTP() == null
                    ? 0 : fm.getHeuresTP();

            if (candidats.isEmpty()
                    && (heuresCours > 0 || heuresTP > 0)) {

                throw new RuntimeException(
                        "Aucun professeur n'a choisi la matière : "
                                + fm.getMatiere().getNom()
                );
            }

            professeursParMatiere.put(matiereId, candidats);
        }

        // =================================================
        // 2. SUIVRE LA CHARGE TOTALE DE CHAQUE PROFESSEUR
        // =================================================

        // La charge est conservée pendant toute la génération.
        // Elle n'est pas remise à zéro pour chaque matière.

        Map<Long, Float> chargeProfesseurs = new HashMap<>();

        // =================================================
        // 3. PRÉPARER LES NOUVELLES AFFECTATIONS
        // =================================================

        List<Enseignement> nouvellesAffectations =
                new ArrayList<>();

        for (Groupe groupe : groupesTries) {

            for (FiliereMatiere fm : matieresTriees) {

                Matiere matiere = fm.getMatiere();

                float heuresCours = fm.getHeuresCours() == null
                        ? 0 : fm.getHeuresCours();

                float heuresTP = fm.getHeuresTP() == null
                        ? 0 : fm.getHeuresTP();

                if (heuresCours < 0 || heuresTP < 0) {
                    throw new RuntimeException(
                            "Les heures de la matière "
                                    + matiere.getNom()
                                    + " ne peuvent pas être négatives"
                    );
                }

                if (heuresCours == 0 && heuresTP == 0) {
                    continue;
                }

                // Vérifier les unités des heures.

                if (heuresCours > 0) {
                    calculerUnites(
                            heuresCours,
                            1.5f,
                            "cours"
                    );
                }

                if (heuresTP > 0) {
                    calculerUnites(
                            heuresTP,
                            3f,
                            "TP"
                    );
                }

                List<Professeur> candidats =
                        professeursParMatiere.get(matiere.getId());

                if (candidats == null || candidats.isEmpty()) {
                    throw new RuntimeException(
                            "Aucun professeur n'a choisi la matière : "
                                    + matiere.getNom()
                    );
                }

                // =================================================
                // 4. GARDER UNIQUEMENT LES PROFESSEURS DISPONIBLES
                // =================================================

                List<Professeur> disponibles = candidats.stream()
                        .filter(p ->
                                estDisponiblePourAffectation(
                                        p,
                                        heuresCours,
                                        heuresTP
                                )
                        )
                        .toList();

                if (disponibles.isEmpty()) {
                    throw new RuntimeException(
                            "Aucun professeur ayant choisi la matière "
                                    + matiere.getNom()
                                    + " ne possède les disponibilités "
                                    + "nécessaires pour assurer les cours "
                                    + "et les TP du groupe "
                                    + groupe.getNom()
                    );
                }

                // =================================================
                // 5. CHOISIR LE PROFESSEUR LE MOINS CHARGÉ
                // =================================================

                Professeur professeurChoisi = disponibles.stream()
                        .min(
                                Comparator.comparingDouble(
                                        (Professeur p) ->
                                                chargeProfesseurs.getOrDefault(
                                                        p.getId(),
                                                        0f
                                                )
                                ).thenComparing(Professeur::getId)
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Impossible de choisir un professeur"
                                )
                        );

                // =================================================
                // 6. CRÉER UNE SEULE AFFECTATION
                // =================================================

                // IMPORTANT :
                // Le même professeur reçoit les heures de cours
                // ET les heures de TP pour ce groupe et cette matière.

                float heuresCoursAffectees = heuresCours;

                if (heuresCours > 0) {
                    int nombreSeancesCours =
                            calculerUnites(
                                    heuresCours,
                                    1.5f,
                                    "cours"
                            );

                    heuresCoursAffectees =
                            nombreSeancesCours * 1.5f;
                }

                Enseignement enseignement = Enseignement.builder()
                        .professeur(professeurChoisi)
                        .matiere(matiere)
                        .filiere(filiere)
                        .groupe(groupe)
                        .heuresCours(heuresCoursAffectees)
                        .heuresTP(heuresTP)
                        .genereAutomatiquement(true)
                        .build();

                nouvellesAffectations.add(enseignement);

                // =================================================
                // 7. METTRE À JOUR LA CHARGE DU PROFESSEUR
                // =================================================

                float totalHeures = heuresCoursAffectees + heuresTP;

                chargeProfesseurs.merge(
                        professeurChoisi.getId(),
                        totalHeures,
                        Float::sum
                );
            }
        }

        // =================================================
        // 8. REMPLACER LES ANCIENNES AFFECTATIONS AUTOMATIQUES
        // =================================================

        enseignementRepository.deleteAutomaticAssignmentsByFiliereAndSemester(
                filiereId,
                semestre
        );

        return enseignementRepository.saveAll(
                nouvellesAffectations
        );
    }

    // =====================================================
    // CRUD METHODS
    // =====================================================

    public EnseignementDTO ajouterEnseignement(
            Long professeurId,
            Long matiereId,
            Long filiereId,
            Float heuresCours,
            Float heuresTP
    ) {

        if (heuresCours == null || heuresTP == null
                || heuresCours < 0 || heuresTP < 0) {
            throw new IllegalArgumentException(
                    "Les heures doivent être positives ou nulles"
            );
        }

        Professeur professeur = professeurRepository.findById(professeurId)
                .orElseThrow(() ->
                        new RuntimeException("Professeur introuvable"));

        Matiere matiere = matiereRepository.findById(matiereId)
                .orElseThrow(() ->
                        new RuntimeException("Matière introuvable"));

        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() ->
                        new RuntimeException("Filière introuvable"));

        boolean existe = enseignementRepository
                .existsByProfesseurAndMatiereAndFiliere(
                        professeur,
                        matiere,
                        filiere
                );

        if (existe) {
            throw new RuntimeException(
                    "Ce professeur est déjà affecté à cette matière dans cette filière"
            );
        }

        Enseignement enseignement = Enseignement.builder()
                .professeur(professeur)
                .matiere(matiere)
                .filiere(filiere)
                .heuresCours(heuresCours)
                .heuresTP(heuresTP)
                .build();

        Enseignement saved = enseignementRepository.save(enseignement);

        return convertirEnDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<EnseignementDTO> getAllEnseignements() {

        return enseignementRepository.findAll()
                .stream()
                .map(this::convertirEnDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnseignementDTO getEnseignementById(Long id) {

        Enseignement enseignement = enseignementRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Enseignement introuvable"));

        return convertirEnDTO(enseignement);
    }

    @Transactional(readOnly = true)
    public List<EnseignementDTO> getByProfesseur(Long professeurId) {

        Professeur professeur = professeurRepository.findById(professeurId)
                .orElseThrow(() ->
                        new RuntimeException("Professeur introuvable"));

        return enseignementRepository.findByProfesseur(professeur)
                .stream()
                .map(this::convertirEnDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnseignementDTO> getByFiliere(Long filiereId) {

        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() ->
                        new RuntimeException("Filière introuvable"));

        return enseignementRepository.findByFiliere(filiere)
                .stream()
                .map(this::convertirEnDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnseignementDTO> getByMatiereAndFiliere(
            Long matiereId,
            Long filiereId
    ) {

        Matiere matiere = matiereRepository.findById(matiereId)
                .orElseThrow(() ->
                        new RuntimeException("Matière introuvable"));

        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() ->
                        new RuntimeException("Filière introuvable"));

        return enseignementRepository
                .findByMatiereAndFiliere(matiere, filiere)
                .stream()
                .map(this::convertirEnDTO)
                .toList();
    }

    public EnseignementDTO modifierHeures(
            Long id,
            Float heuresCours,
            Float heuresTP
    ) {

        if (heuresCours == null || heuresTP == null
                || heuresCours < 0 || heuresTP < 0) {
            throw new IllegalArgumentException(
                    "Les heures doivent être positives ou nulles"
            );
        }

        Enseignement enseignement = enseignementRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Enseignement introuvable"));

        enseignement.setHeuresCours(heuresCours);
        enseignement.setHeuresTP(heuresTP);

        Enseignement updated = enseignementRepository.save(enseignement);

        return convertirEnDTO(updated);
    }

    public void supprimerEnseignement(Long id) {

        Enseignement enseignement = enseignementRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Enseignement introuvable"));

        enseignementRepository.delete(enseignement);
    }
}
