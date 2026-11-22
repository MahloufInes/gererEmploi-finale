
package com.edt.repository;

import com.edt.entity.Enseignement;
import com.edt.entity.Professeur;
import com.edt.entity.Matiere;
import com.edt.entity.Filiere;
import com.edt.entity.Groupe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnseignementRepository
        extends JpaRepository<Enseignement, Long> {

    List<Enseignement> findByProfesseur(Professeur professeur);

    List<Enseignement> findByMatiere(Matiere matiere);

    List<Enseignement> findByFiliere(Filiere filiere);

    List<Enseignement> findByGroupe(Groupe groupe);

    List<Enseignement> findByProfesseurAndFiliere(
            Professeur professeur, Filiere filiere);

    List<Enseignement> findByProfesseurAndMatiere(
            Professeur professeur, Matiere matiere);

    List<Enseignement> findByMatiereAndFiliere(
            Matiere matiere, Filiere filiere);

    List<Enseignement> findByMatiereAndGroupe(
            Matiere matiere, Groupe groupe);

    List<Enseignement> findByProfesseurAndMatiereAndGroupe(
            Professeur professeur, Matiere matiere, Groupe groupe);

    List<Enseignement> findByFiliereIdAndGenereAutomatiquementTrue(
            Long filiereId);

    // Affectations automatiques d'un professeur.
    // Elles permettent d'identifier les filières et semestres concernés.
    @Query("""
        SELECT DISTINCT e
        FROM Enseignement e
        JOIN FETCH e.professeur p
        JOIN FETCH e.filiere f
        JOIN FETCH e.groupe g
        JOIN FETCH e.matiere m
        WHERE p.id = :professeurId
          AND e.genereAutomatiquement = true
    """)
    List<Enseignement> findAffectationsAutomatiquesByProfesseur(
            @Param("professeurId") Long professeurId
    );

    // Récupérer les affectations existantes d'une filière et d'un semestre.
    // Aucune nouvelle répartition des professeurs n'est effectuée.
    @Query("""
        SELECT DISTINCT e
        FROM Enseignement e
        JOIN FETCH e.professeur p
        JOIN FETCH e.matiere m
        JOIN FETCH e.groupe g
        WHERE e.filiere.id = :filiereId
          AND e.genereAutomatiquement = true
          AND :semestre MEMBER OF g.semestres
    """)
    List<Enseignement> findAffectationsAutomatiquesByFiliereAndSemestre(
            @Param("filiereId") Long filiereId,
            @Param("semestre") String semestre
    );

    @Modifying
    @Query("""
        DELETE FROM Enseignement e
        WHERE e.filiere.id = :filiereId
          AND e.genereAutomatiquement = true
          AND e.groupe IS NOT NULL
          AND EXISTS (
              SELECT g.id
              FROM Groupe g
              JOIN g.semestres s
              WHERE g.id = e.groupe.id
                AND s = :semestre
          )
    """)
    void deleteAutomaticAssignmentsByFiliereAndSemester(
            @Param("filiereId") Long filiereId,
            @Param("semestre") String semestre
    );

    boolean existsByProfesseurAndMatiereAndFiliere(
            Professeur professeur,
            Matiere matiere,
            Filiere filiere
    );
}
