
package com.edt.dto;

import java.time.LocalDate;

public record SeanceResponse(
        Long id,
        LocalDate date,
        String jour,
        String heureDebut,
        String heureFin,
        String type,
        String statut,
        String matiereNom,
        String professeurNom,
        String salleNom,
        String groupeNom
) {
}
