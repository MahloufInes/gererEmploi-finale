
package com.edt.repository;

import com.edt.entity.Seance;
import com.edt.entity.EmploiDuTemps;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SeanceRepository extends JpaRepository<Seance, Long> {

    List<Seance> findByEmploiDuTempsId(Long emploiId);

    List<Seance> findByProfesseurId(Long professeurId);

    List<Seance> findByGroupeId(Long groupeId);

    List<Seance> findByGroupeIdAndDate(
            Long groupeId,
            LocalDate date
    );

    List<Seance> findByGroupeIdAndDateBetween(
            Long groupeId,
            LocalDate debut,
            LocalDate fin
    );

    @Query("""
        SELECT DISTINCT s.emploiDuTemps
        FROM Seance s
        WHERE s.groupe.filiere.id = :filiereId
        AND s.emploiDuTemps.semestre = :semestre
    """)
    List<EmploiDuTemps> findTimetablesByFiliereAndSemestre(
            @Param("filiereId") Long filiereId,
            @Param("semestre") String semestre
    );

    // Consultation des séances existantes par groupe et semestre
    @Query("""
        SELECT s
        FROM Seance s
        JOIN FETCH s.emploiDuTemps e
        JOIN FETCH s.creneau c
        JOIN FETCH s.matiere m
        JOIN FETCH s.professeur p
        JOIN FETCH s.salle sa
        JOIN FETCH s.groupe g
        WHERE g.id = :groupeId
        AND e.semestre = :semestre
    """)
    List<Seance> consulterSeancesParGroupeEtSemestre(
            @Param("groupeId") Long groupeId,
            @Param("semestre") String semestre
    );

    // Séances d'un groupe pour un semestre (visibles par l'étudiant)
    @Query("""
    SELECT s
    FROM Seance s
    JOIN FETCH s.emploiDuTemps e
    JOIN FETCH s.creneau c
    JOIN FETCH s.matiere m
    JOIN FETCH s.professeur p
    JOIN FETCH s.salle sa
    JOIN FETCH s.groupe g
    WHERE g.id = :groupeId
    AND e.semestre = :semestre
    ORDER BY c.jour, c.heureDebut
""")
    List<Seance> consulterSeancesPublieesParGroupeEtSemestre(
            @Param("groupeId") Long groupeId,
            @Param("semestre") String semestre
    );

    // Consultation des séances par professeur
    @Query("""
        SELECT s
        FROM Seance s
        JOIN FETCH s.creneau c
        JOIN FETCH s.matiere m
        JOIN FETCH s.professeur p
        JOIN FETCH s.salle sa
        JOIN FETCH s.groupe g
        WHERE p.id = :professeurId
        ORDER BY c.jour, c.heureDebut
    """)
    List<Seance> consulterSeancesParProfesseur(
            @Param("professeurId") Long professeurId
    );

    // Vérification des conflits
    boolean existsBySalleIdAndCreneauIdAndDate(
            Long salleId,
            Long creneauId,
            LocalDate date
    );

    boolean existsByProfesseurIdAndCreneauIdAndDate(
            Long professeurId,
            Long creneauId,
            LocalDate date
    );

    boolean existsByGroupeIdAndCreneauIdAndDate(
            Long groupeId,
            Long creneauId,
            LocalDate date
    );
    @Query("""
    SELECT DISTINCT s.emploiDuTemps
    FROM Seance s
    WHERE s.groupe.id = :groupeId
    AND s.emploiDuTemps.semestre = :semestre
""")
    List<EmploiDuTemps> findEmploisParGroupeEtSemestre(
            @Param("groupeId") Long groupeId,
            @Param("semestre") String semestre);
}
