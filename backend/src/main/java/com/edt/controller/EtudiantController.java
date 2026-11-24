
package com.edt.controller;

import com.edt.dto.EtudiantRequest;
import com.edt.dto.EtudiantResponse;
import com.edt.service.EtudiantService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/etudiants")
@CrossOrigin(origins = {
        "http://localhost:4200",
        "https://gereremploi-finale-front.onrender.com"
})
public class EtudiantController {

    private final EtudiantService etudiantService;

    public EtudiantController(EtudiantService etudiantService) {
        this.etudiantService = etudiantService;
    }

    // Consulter tous les étudiants (administrateurs)
    @GetMapping
    public List<EtudiantResponse> afficherTous() {
        return etudiantService.afficherTous();
    }

    // Consulter le profil de l'étudiant connecté
    @GetMapping("/mon-profil")
    public EtudiantResponse afficherMonProfil(Authentication authentication) {
        String email = authentication.getName();
        return etudiantService.afficherMonProfil(email);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EtudiantResponse ajouter(
            @Valid @RequestBody EtudiantRequest request) {
        return etudiantService.ajouter(request);
    }

    // Modifier un étudiant
    @PutMapping("/{id}")
    public EtudiantResponse modifier(
            @PathVariable Long id,
            @Valid @RequestBody EtudiantRequest request) {
        return etudiantService.modifier(id, request);
    }

    // Supprimer un étudiant
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        etudiantService.supprimer(id);
    }
}
