package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "reactions")
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "feed_item_id", nullable = false)
    private UUID feedItemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "kind", length = 20)
    private String kind;

    public Reaction() {}

    public UUID getId()                    { return id; }

    public UUID getFeedItemId()            { return feedItemId; }
    public void setFeedItemId(UUID v)      { this.feedItemId = v; }

    public UUID getUserId()                { return userId; }
    public void setUserId(UUID v)          { this.userId = v; }

    public String getKind()                { return kind; }
    public void setKind(String v)          { this.kind = v; }
}
