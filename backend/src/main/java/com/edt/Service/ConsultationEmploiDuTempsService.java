package com.edt.service;

import com.edt.dto.SeanceResponse;
import com.edt.entity.Etudiant;
import com.edt.entity.Seance;
import com.edt.entity.Utilisateur;

import com.edt.repository.EtudiantRepository;
import com.edt.repository.SeanceRepository;
import com.edt.repository.UtilisateurRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ConsultationEmploiDuTempsService {

    private final SeanceRepository seanceRepository;
    private final EtudiantRepository etudiantRepository;
    private final UtilisateurRepository utilisateurRepository;

    public ConsultationEmploiDuTempsService(
            SeanceRepository seanceRepository,
            EtudiantRepository etudiantRepository,
            UtilisateurRepository utilisateurRepository) {

        this.seanceRepository = seanceRepository;
        this.etudiantRepository = etudiantRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    // Admin : consultation par groupe et semestre
    @Transactional(readOnly = true)
    public List<SeanceResponse> consulterEmploiDuTemps(
            Long groupeId,
            String semestre) {

        return seanceRepository
                .consulterSeancesParGroupeEtSemestre(groupeId, semestre)
                .stream()
                .map(this::convertirEnResponse)
                .toList();
    }

    // Étudiant connecté : séances de son groupe pour un semestre
    @Transactional(readOnly = true)
    public List<SeanceResponse> consulterMonEmploiDuTemps(
            String email,
            String semestre) {

        Etudiant etudiant = trouverEtudiant(email);

        return seanceRepository
                .consulterSeancesPublieesParGroupeEtSemestre(
                        etudiant.getGroupe().getId(),
                        semestre)
                .stream()
                .map(this::convertirEnResponse)
                .toList();
    }

    // Étudiant connecté : semestres de son groupe
    @Transactional(readOnly = true)
    public List<String> consulterMesSemestres(String email) {

        Etudiant etudiant = trouverEtudiant(email);

        return etudiant.getGroupe().getSemestres()
                .stream()
                .sorted()
                .toList();
    }

    // Professeur connecté : toutes ses séances
    @Transactional(readOnly = true)
    public List<SeanceResponse> consulterMonEmploiProfesseur(String email) {

        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Professeur introuvable."));

        return seanceRepository
                .consulterSeancesParProfesseur(utilisateur.getId())
                .stream()
                .map(this::convertirEnResponse)
                .toList();
    }

    private Etudiant trouverEtudiant(String email) {

        Etudiant etudiant = etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Étudiant introuvable."));

        if (etudiant.getGroupe() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Aucun groupe n'est associé à cet étudiant.");
        }

        return etudiant;
    }

    private SeanceResponse convertirEnResponse(Seance seance) {

        return new SeanceResponse(
                seance.getId(),
                seance.getDate(),
                seance.getCreneau().getJour().name(),
                seance.getCreneau().getHeureDebut().toString(),
                seance.getCreneau().getHeureFin().toString(),
                seance.getType().name(),
                seance.getStatut().name(),
                seance.getMatiere().getNom(),
                seance.getProfesseur().getPrenom()
                        + " "
                        + seance.getProfesseur().getNom(),
                seance.getSalle().getNom(),
                seance.getGroupe().getNom()
        );
    }
}