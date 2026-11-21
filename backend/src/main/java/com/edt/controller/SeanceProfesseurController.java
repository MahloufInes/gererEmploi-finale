package com.edt.controller;

import com.edt.dto.SeanceResponse;
import com.edt.service.ConsultationEmploiDuTempsService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seances")
public class SeanceProfesseurController {

    private final ConsultationEmploiDuTempsService service;

    public SeanceProfesseurController(ConsultationEmploiDuTempsService service) {
        this.service = service;
    }

    // Professeur : son propre emploi du temps (identifié par le JWT)
    @GetMapping("/mon-prof-emploi")
    public List<SeanceResponse> monEmploi(Authentication authentication) {
        return service.consulterMonEmploiProfesseur(authentication.getName());
    }
}