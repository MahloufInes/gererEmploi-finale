
package com.edt.controller;

import com.edt.dto.DisponibiliteRequest;
import com.edt.service.DisponibiliteService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/disponibilites")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:4200",
        "https://gereremploi-finale-front.onrender.com"
})
public class DisponibiliteController {

    private final DisponibiliteService disponibiliteService;

    // Enregistrer les disponibilités
    @PostMapping("/professeur/{professeurId}")
    public ResponseEntity<Map<String, Object>> enregistrerDisponibilites(
            @PathVariable Long professeurId,
            @RequestBody DisponibiliteRequest request
    ) {

        int nombreAjoute =
                disponibiliteService.enregistrerDisponibilites(
                        professeurId,
                        request
                );

        Map<String, Object> response = Map.of(
                "message", "Disponibilités enregistrées avec succès.",
                "nombreAjoute", nombreAjoute
        );

        return ResponseEntity.ok(response);
    }

    // Récupérer les disponibilités du professeur
    @GetMapping("/professeur/{professeurId}")
    public ResponseEntity<List<Map<String, String>>> obtenirDisponibilites(
            @PathVariable Long professeurId
    ) {

        List<Map<String, String>> disponibilites =
                disponibiliteService.obtenirDisponibilites(professeurId);

        return ResponseEntity.ok(disponibilites);
    }
}
