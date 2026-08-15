package com.betai.domain.history;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "user_saved_batches")
public class UserSavedBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 128)
    private String batchName;

    @OneToMany(mappedBy = "userSavedBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserSavedBatchItem> items = new ArrayList<>();

    public void addItem(UserSavedBatchItem item) {
        items.add(item);
        item.setUserSavedBatch(this);
    }

    public void removeItem(UserSavedBatchItem item) {
        items.remove(item);
        item.setUserSavedBatch(null);
    }
}
