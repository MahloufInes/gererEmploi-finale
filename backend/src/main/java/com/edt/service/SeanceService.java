
package com.edt.service;

import com.edt.dto.SeanceResponse;
import com.edt.entity.Professeur;
import com.edt.entity.Seance;
import com.edt.entity.Utilisateur;
import com.edt.repository.SeanceRepository;
import com.edt.repository.UtilisateurRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeanceService {

    private final SeanceRepository seanceRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional(readOnly = true)
    public List<SeanceResponse> consulterMonEmploi(String email) {

        // 1. Retrieve the authenticated user using their email
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Utilisateur introuvable"));

        // 2. Verify that the user is a professor
        if (!(utilisateur instanceof Professeur professeur)) {
            throw new RuntimeException(
                    "Cet utilisateur n'est pas un professeur");
        }

        // 3. Retrieve the professor's timetable sessions
        List<Seance> seances =
                seanceRepository.consulterSeancesParProfesseur(
                        professeur.getId()
                );

        // 4. Convert sessions into DTOs
        return seances.stream()
                .map(s -> new SeanceResponse(
                        s.getId(),
                        s.getDate(),
                        s.getCreneau().getJour().name(),
                        s.getCreneau().getHeureDebut().toString(),
                        s.getCreneau().getHeureFin().toString(),
                        s.getType() != null ? s.getType().name() : null,
                        s.getStatut() != null ? s.getStatut().name() : null,
                        s.getMatiere().getNom(),
                        s.getProfesseur().getPrenom() + " "
                                + s.getProfesseur().getNom(),
                        s.getSalle().getNom(),
                        s.getGroupe().getNom()
                ))
                .toList();
    }
}
