
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

@Service
@RequiredArgsConstructor
public class DisponibiliteService {

    private final DisponibiliteRepository disponibiliteRepository;
    private final CreneauHoraireRepository creneauHoraireRepository;
    private final EntityManager entityManager;

    @Transactional
    public int enregistrerDisponibilites(
            Long professeurId,
            DisponibiliteRequest request
    ) {

        // 1. Vérifier que le professeur existe
        Professeur professeur = entityManager.find(
                Professeur.class,
                professeurId
        );

        if (professeur == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Professeur introuvable : " + professeurId
            );
        }

        if (request == null || request.getCreneaux() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Aucun créneau reçu."
            );
        }

        int nombreAjoute = 0;

        // 2. Parcourir les créneaux sélectionnés
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

            // 3. Convertir les valeurs reçues
            try {
                jour = Jour.valueOf(
                        selection.getJour().trim().toUpperCase()
                );

                heureDebut = LocalTime.parse(
                        selection.getHeureDebut()
                );

                heureFin = LocalTime.parse(
                        selection.getHeureFin()
                );

            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Jour ou horaire invalide.",
                        e
                );
            }

            // 4. Retrouver le vrai créneau dans MySQL
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

            // 5. Vérifier si la disponibilité existe déjà
            boolean existe =
                    disponibiliteRepository
                            .existsByProfesseurIdAndCreneauId(
                                    professeurId,
                                    creneau.getId()
                            );

            // 6. Enregistrer uniquement si elle n'existe pas
            if (!existe) {

                Disponibilite disponibilite = new Disponibilite();

                disponibilite.setProfesseur(professeur);
                disponibilite.setCreneau(creneau);

                disponibiliteRepository.save(disponibilite);

                nombreAjoute++;
            }
        }

        return nombreAjoute;
    }
}
