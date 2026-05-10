package com.yardenbental_danielcohen_shlomoedelstein.carn_go.model;

import java.io.Serializable;

public class Booking implements Serializable {
    private String id;
    private String carId;
    private String userId;
    private String carName;
    private String carImageUrl;
    private long startTime;
    private long endTime;
    private double totalCost;
    private long timestamp;

    public Booking() {}

    public Booking(String id, String carId, String userId, String carName, String carImageUrl, long startTime, long endTime, double totalCost, long timestamp) {
        this.id = id;
        this.carId = carId;
        this.userId = userId;
        this.carName = carName;
        this.carImageUrl = carImageUrl;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalCost = totalCost;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getCarId() { return carId; }
    public String getUserId() { return userId; }
    public String getCarName() { return carName; }
    public String getCarImageUrl() { return carImageUrl; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public double getTotalCost() { return totalCost; }
    public long getTimestamp() { return timestamp; }
}