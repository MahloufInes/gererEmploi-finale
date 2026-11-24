
package com.edt.entity;

import com.edt.entity.enums.StatutPreferenceGroupe;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "preference_groupe",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"professeur_id", "groupe_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenceGroupe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professeur_id", nullable = false)
    private Professeur professeur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "groupe_id", nullable = false)
    private Groupe groupe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutPreferenceGroupe statut;
}
