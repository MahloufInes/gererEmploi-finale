
package com.edt.repository;

import com.edt.dto.PreferenceGroupeResponse;
import com.edt.entity.PreferenceGroupe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenceGroupeRepository
        extends JpaRepository<PreferenceGroupe, Long> {

    Optional<PreferenceGroupe> findByProfesseurIdAndGroupeId(
            Long professeurId,
            Long groupeId
    );

    List<PreferenceGroupe> findByProfesseurId(Long professeurId);

    List<PreferenceGroupe> findByGroupeId(Long groupeId);

    boolean existsByProfesseurIdAndGroupeId(
            Long professeurId,
            Long groupeId
    );

    void deleteByProfesseurIdAndGroupeId(
            Long professeurId,
            Long groupeId
    );

    @Query("""
        SELECT new com.edt.dto.PreferenceGroupeResponse(
            pg.id,
            p.id,
            g.id,
            g.nom,
            pg.statut
        )
        FROM PreferenceGroupe pg
        JOIN pg.professeur p
        JOIN pg.groupe g
        WHERE p.id = :professeurId
        ORDER BY g.nom
    """)
    List<PreferenceGroupeResponse> findResponsesByProfesseurId(
            @Param("professeurId") Long professeurId
    );

    @Query("""
        SELECT new com.edt.dto.PreferenceGroupeResponse(
            pg.id,
            p.id,
            g.id,
            g.nom,
            pg.statut
        )
        FROM PreferenceGroupe pg
        JOIN pg.professeur p
        JOIN pg.groupe g
        WHERE g.id = :groupeId
        ORDER BY p.id
    """)
    List<PreferenceGroupeResponse> findResponsesByGroupeId(
            @Param("groupeId") Long groupeId
    );
}
