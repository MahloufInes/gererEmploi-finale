
package com.edt.controller;

import com.edt.dto.SeanceResponse;
import com.edt.service.SeanceService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seances")
public class SeanceController {

    private final SeanceService seanceService;

    public SeanceController(SeanceService seanceService) {
        this.seanceService = seanceService;
    }

    @GetMapping("/mon-emploi")
    public ResponseEntity<List<SeanceResponse>> consulterMonEmploi(
            Authentication authentication) {

        List<SeanceResponse> resultat =
                seanceService.consulterMonEmploi(authentication.getName());

        return ResponseEntity.ok(resultat);
    }
}
