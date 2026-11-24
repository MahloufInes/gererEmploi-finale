
package com.edt.service;

import com.edt.dto.PreferenceGroupeResponse;
import com.edt.entity.*;
import com.edt.entity.enums.StatutPreferenceGroupe;
import com.edt.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PreferenceGroupeService {

    private final PreferenceGroupeRepository preferenceRepository;
    private final ProfesseurRepository professeurRepository;
    private final GroupeRepository groupeRepository;

    // Service responsable de la régénération des emplois du temps
    private final GenerationEmploiDuTempsService generationEmploiDuTempsService;

    /**
     * Ajouter ou modifier la préférence d'un professeur
     * pour un groupe.
     */
    public PreferenceGroupe ajouterOuModifierPreference(
            Long professeurId,
            Long groupeId,
            StatutPreferenceGroupe statut
    ) {

        if (statut == null) {
            throw new RuntimeException("Le statut est obligatoire");
        }

        Professeur professeur = professeurRepository.findById(professeurId)
                .orElseThrow(() ->
                        new RuntimeException("Professeur introuvable"));

        Groupe groupe = groupeRepository.findById(groupeId)
                .orElseThrow(() ->
                        new RuntimeException("Groupe introuvable"));

        PreferenceGroupe preference = preferenceRepository
                .findByProfesseurIdAndGroupeId(professeurId, groupeId)
                .orElseGet(() -> PreferenceGroupe.builder()
                        .professeur(professeur)
                        .groupe(groupe)
                        .build());

        // Enregistrer le nouveau statut
        preference.setStatut(statut);

        PreferenceGroupe preferenceEnregistree =
                preferenceRepository.save(preference);

        // Si le professeur refuse le groupe,
        // régénérer les emplois du temps concernés.
        if (statut == StatutPreferenceGroupe.REFUSE) {

            generationEmploiDuTempsService
                    .regenererEmploiDuTempsApresRefus(
                            professeurId,
                            groupeId
                    );
        }

        return preferenceEnregistree;
    }

    /**
     * Récupérer les préférences de groupes d'un professeur.
     */
    @Transactional(readOnly = true)
    public List<PreferenceGroupeResponse> getByProfesseur(
            Long professeurId
    ) {

        if (!professeurRepository.existsById(professeurId)) {
            throw new RuntimeException("Professeur introuvable");
        }

        return preferenceRepository
                .findResponsesByProfesseurId(professeurId);
    }

    /**
     * Récupérer les préférences des professeurs pour un groupe.
     */
    @Transactional(readOnly = true)
    public List<PreferenceGroupeResponse> getByGroupe(
            Long groupeId
    ) {

        if (!groupeRepository.existsById(groupeId)) {
            throw new RuntimeException("Groupe introuvable");
        }

        return preferenceRepository
                .findResponsesByGroupeId(groupeId);
    }

    /**
     * Supprimer la préférence d'un professeur pour un groupe.
     */
    public void supprimerPreference(
            Long professeurId,
            Long groupeId
    ) {

        if (!preferenceRepository.existsByProfesseurIdAndGroupeId(
                professeurId, groupeId)) {

            throw new RuntimeException(
                    "Cette préférence professeur-groupe n'existe pas"
            );
        }

        preferenceRepository.deleteByProfesseurIdAndGroupeId(
                professeurId,
                groupeId
        );
    }
}
