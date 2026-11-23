
package com.edt.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DisponibiliteRequest {

    private List<CreneauSelectionne> creneaux;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreneauSelectionne {

        private String jour;

        private String heureDebut;

        private String heureFin;
    }
}
