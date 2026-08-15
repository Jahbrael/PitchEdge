package com.betai.domain.odds;

import com.betai.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "bookmakers",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_bookmakers_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_bookmakers_active", columnList = "active")
        }
)
public class Bookmaker extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;
}
