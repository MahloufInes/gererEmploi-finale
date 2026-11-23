
package com.edt.service;

import com.edt.dto.DisponibiliteRequest;
import com.edt.entity.CreneauHoraire;
import com.edt.entity.Disponibilite;
import com.edt.entity.Professeur;
import com.edt.entity.enums.Jour;
import com.edt.repository.CreneauHoraireRepository;
import com.edt.repository.DisponibiliteRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DisponibiliteService {

    private final DisponibiliteRepository disponibiliteRepository;
    private final CreneauHoraireRepository creneauHoraireRepository;
    private final GenerationEmploiDuTempsService generationService;
    private final EntityManager entityManager;


    // =========================================================
    // ENREGISTRER ET SYNCHRONISER LES DISPONIBILITÉS
    // =========================================================

    @Transactional
    public int enregistrerDisponibilites(
            Long professeurId,
            DisponibiliteRequest request
    ) {

        // 1. Vérifier l'identifiant du professeur
        if (professeurId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identifiant du professeur invalide."
            );
        }

        // 2. Vérifier que le professeur existe
        Professeur professeur = entityManager.find(
                Professeur.class,
                professeurId
        );

        if (professeur == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Professeur introuvable."
            );
        }

        // 3. Vérifier la requête
        if (request == null || request.getCreneaux() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Liste des créneaux invalide."
            );
        }

        // 4. Récupérer les anciennes disponibilités
        List<Disponibilite> anciennesDisponibilites =
                disponibiliteRepository.findByProfesseurId(
                        professeurId
                );

        // 5. Préparer les nouvelles disponibilités
        Set<Long> creneauxIds = new HashSet<>();

        List<Disponibilite> nouvellesDisponibilites =
                new ArrayList<>();

        // 6. Valider chaque créneau avant toute suppression
        for (DisponibiliteRequest.CreneauSelectionne selection
                : request.getCreneaux()) {

            if (selection == null
                    || selection.getJour() == null
                    || selection.getHeureDebut() == null
                    || selection.getHeureFin() == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Informations du créneau incomplètes."
                );
            }

            Jour jour;
            LocalTime heureDebut;
            LocalTime heureFin;

            try {

                jour = Jour.valueOf(
                        selection.getJour()
                                .trim()
                                .toUpperCase(Locale.ROOT)
                );

                heureDebut = LocalTime.parse(
                        selection.getHeureDebut().trim()
                );

                heureFin = LocalTime.parse(
                        selection.getHeureFin().trim()
                );

            } catch (IllegalArgumentException
                     | DateTimeParseException e) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Jour ou horaire invalide.",
                        e
                );
            }

            // 7. Vérifier l'ordre des horaires
            if (!heureFin.isAfter(heureDebut)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "L'heure de fin doit être après l'heure de début."
                );
            }

            // 8. Retrouver le créneau dans la base de données
            CreneauHoraire creneau =
                    creneauHoraireRepository
                            .findByJourAndHeureDebutAndHeureFin(
                                    jour,
                                    heureDebut,
                                    heureFin
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST,
                                            "Créneau introuvable : "
                                                    + jour + " "
                                                    + heureDebut + "-"
                                                    + heureFin
                                    )
                            );

            // 9. Éviter les doublons
            if (creneauxIds.add(creneau.getId())) {

                Disponibilite disponibilite =
                        new Disponibilite();

                disponibilite.setProfesseur(professeur);
                disponibilite.setCreneau(creneau);

                nouvellesDisponibilites.add(disponibilite);
            }
        }

        /*
         * À partir d'ici, tous les nouveaux créneaux sont validés.
         */

        // 10. Supprimer les anciennes disponibilités
        disponibiliteRepository.deleteAll(
                anciennesDisponibilites
        );

        disponibiliteRepository.flush();

        // 11. Enregistrer les nouvelles disponibilités
        disponibiliteRepository.saveAll(
                nouvellesDisponibilites
        );

        disponibiliteRepository.flush();

        // 12. Régénérer les emplois du temps concernés
        // Sans modifier les affectations existantes
        generationService.regenererEmploisDuTempsProfesseur(
                professeurId
        );

        // 13. Retourner le nombre de disponibilités enregistrées
        return nouvellesDisponibilites.size();
    }


    // =========================================================
    // RÉCUPÉRER LES DISPONIBILITÉS D'UN PROFESSEUR
    // =========================================================

    @Transactional(readOnly = true)
    public List<Map<String, String>> obtenirDisponibilites(
            Long professeurId
    ) {

        if (professeurId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identifiant du professeur invalide."
            );
        }

        Professeur professeur = entityManager.find(
                Professeur.class,
                professeurId
        );

        if (professeur == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Professeur introuvable."
            );
        }

        List<Disponibilite> disponibilites =
                disponibiliteRepository.findByProfesseurId(
                        professeurId
                );

        return disponibilites.stream()
                .map(disponibilite -> {

                    CreneauHoraire creneau =
                            disponibilite.getCreneau();

                    return Map.of(
                            "jour",
                            creneau.getJour().name(),

                            "heureDebut",
                            creneau.getHeureDebut().toString(),

                            "heureFin",
                            creneau.getHeureFin().toString()
                    );
                })
                .toList();
    }
}
