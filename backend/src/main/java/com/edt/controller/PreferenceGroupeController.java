
package com.edt.controller;

import com.edt.dto.PreferenceGroupeResponse;
import com.edt.entity.PreferenceGroupe;
import com.edt.entity.enums.StatutPreferenceGroupe;
import com.edt.service.PreferenceGroupeService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/preferences-groupes")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:4200",
        "https://gereremploi-finale-front.onrender.com"
})public class PreferenceGroupeController {

    private final PreferenceGroupeService preferenceService;

    /**
     * Ajouter ou modifier une préférence.
     *
     * Exemple :
     * POST /api/preferences-groupes
     * ?professeurId=1&groupeId=2&statut=REFUSE
     */
    @PostMapping
    public PreferenceGroupe ajouterOuModifierPreference(
            @RequestParam Long professeurId,
            @RequestParam Long groupeId,
            @RequestParam StatutPreferenceGroupe statut
    ) {

        return preferenceService.ajouterOuModifierPreference(
                professeurId,
                groupeId,
                statut
        );
    }

    /**
     * Consulter les préférences d'un professeur.
     */
    @GetMapping("/professeur/{professeurId}")
    public List<PreferenceGroupeResponse> getByProfesseur(
            @PathVariable Long professeurId
    ) {

        return preferenceService.getByProfesseur(professeurId);
    }

    /**
     * Consulter les préférences des professeurs pour un groupe.
     */
    @GetMapping("/groupe/{groupeId}")
    public List<PreferenceGroupeResponse> getByGroupe(
            @PathVariable Long groupeId
    ) {

        return preferenceService.getByGroupe(groupeId);
    }

    /**
     * Supprimer une préférence.
     */
    @DeleteMapping
    public void supprimerPreference(
            @RequestParam Long professeurId,
            @RequestParam Long groupeId
    ) {

        preferenceService.supprimerPreference(
                professeurId,
                groupeId
        );
    }
}
