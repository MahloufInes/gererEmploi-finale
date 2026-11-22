
package com.edt.repository;

import com.edt.dto.PreferenceMatiereResponse;
import com.edt.entity.PreferenceMatiere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreferenceMatiereRepository
        extends JpaRepository<PreferenceMatiere, Long> {

    // Get all subjects associated with a professor
    List<PreferenceMatiere> findByProfesseurId(Long professeurId);

    // Get all professors associated with a subject
    List<PreferenceMatiere> findByMatiereId(Long matiereId);

    // Check whether a professor already has this subject
    boolean existsByProfesseurIdAndMatiereId(
            Long professeurId,
            Long matiereId
    );

    // Delete one subject preference for a professor
    void deleteByProfesseurIdAndMatiereId(
            Long professeurId,
            Long matiereId
    );

    // Delete all subject preferences for a professor
    void deleteByProfesseurId(Long professeurId);


    @Query("""
    SELECT new com.edt.dto.PreferenceMatiereResponse(
        p.id,
        prof.id,
        m.id,
        m.nom
    )
    FROM PreferenceMatiere p
    JOIN p.professeur prof
    JOIN p.matiere m
    WHERE prof.id = :professeurId
""")
    List<PreferenceMatiereResponse> findPreferencesResponseByProfesseurId(
            @Param("professeurId") Long professeurId
    );

}
