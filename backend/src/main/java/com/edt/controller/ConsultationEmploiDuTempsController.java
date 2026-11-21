package com.edt.controller;

import com.edt.dto.SeanceResponse;
import com.edt.service.ConsultationEmploiDuTempsService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultation")
public class ConsultationEmploiDuTempsController {

    private final ConsultationEmploiDuTempsService service;

    public ConsultationEmploiDuTempsController(ConsultationEmploiDuTempsService service) {
        this.service = service;
    }

    // Étudiant : emploi du temps de son propre groupe (le groupe vient du JWT)
    @GetMapping("/mon-emploi")
    public List<SeanceResponse> monEmploi(
            @RequestParam String semestre,
            Authentication authentication) {

        return service.consulterMonEmploiDuTemps(authentication.getName(), semestre);
    }

    // Étudiant : semestres de son groupe
    @GetMapping("/mon-emploi/semestres")
    public List<String> mesSemestres(Authentication authentication) {
        return service.consulterMesSemestres(authentication.getName());
    }

    // Admin : consultation par groupe et semestre
    @GetMapping("/groupe/{groupeId}/semestre/{semestre}")
    public List<SeanceResponse> parGroupe(
            @PathVariable Long groupeId,
            @PathVariable String semestre) {

        return service.consulterEmploiDuTemps(groupeId, semestre);
    }
}