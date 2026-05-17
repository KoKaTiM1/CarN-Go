package com.yardenbental_danielcohen_shlomoedelstein.carn_go.model;

import java.io.Serializable;

public class Booking implements Serializable {
    private String id;
    private String carId;
    private String userId;
    private String ownerId;
    private String carName;
    private String carImageUrl;
    private long startTime;
    private long endTime;
    private double totalCost;
    private long timestamp;
    private String status; // PENDING, APPROVED, REJECTED, COMPLETED
    private String endPhotoUrl;

    public Booking() {}

    public Booking(String id, String carId, String userId, String ownerId, String carName, String carImageUrl, long startTime, long endTime, double totalCost, long timestamp, String status) {
        this.id = id;
        this.carId = carId;
        this.userId = userId;
        this.ownerId = ownerId;
        this.carName = carName;
        this.carImageUrl = carImageUrl;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalCost = totalCost;
        this.timestamp = timestamp;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCarId() { return carId; }
    public void setCarId(String carId) { this.carId = carId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getCarName() { return carName; }
    public void setCarName(String carName) { this.carName = carName; }

    public String getCarImageUrl() { return carImageUrl; }
    public void setCarImageUrl(String carImageUrl) { this.carImageUrl = carImageUrl; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEndPhotoUrl() { return endPhotoUrl; }
    public void setEndPhotoUrl(String endPhotoUrl) { this.endPhotoUrl = endPhotoUrl; }
}
