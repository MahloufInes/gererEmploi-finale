package com.edt.dto;

public record PreferenceMatiereResponse(
        Long id,
        Long professeurId,
        Long matiereId,
        String matiereNom
) {}