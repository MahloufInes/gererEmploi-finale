
package com.edt.dto;

import com.edt.entity.enums.StatutPreferenceGroupe;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceGroupeResponse {

    private Long id;
    private Long professeurId;
    private Long groupeId;
    private String groupeNom;
    private StatutPreferenceGroupe statut;
}
