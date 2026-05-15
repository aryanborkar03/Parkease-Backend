package com.parkease.parkinglot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "parking_lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lotId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private int totalSpots;

    @Column(nullable = false)
    private int availableSpots;

    @Column(nullable = false)
    private String managerEmail;

    @Column(nullable = false)
    private String managerName;

    @Column(nullable = false)
    private boolean isOpen;

    @Column(nullable = false)
    private boolean isApproved = false;

    private LocalTime openTime;
    private LocalTime closeTime;

    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "lot_vehicle_types", joinColumns = @JoinColumn(name = "lot_id"))
    @Column(name = "vehicle_type")
    private List<String> vehicleTypes;

    @Column(nullable = false)
    private boolean isEv;

    @Column(nullable = false)
    private boolean isHandicapped;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
