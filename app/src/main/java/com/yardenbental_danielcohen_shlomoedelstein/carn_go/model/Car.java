package com.yardenbental_danielcohen_shlomoedelstein.carn_go.model;

import java.io.Serializable;

/**
 * Model class representing a Car in the system.
 * Implements Serializable to allow passing Car objects between components via Bundles or Intents.
 */
public class Car implements Serializable {
    private String id;
    private String name;
    private String type;
    private String location;
    private double pricePerHour;
    private double rating;
    private String imageUrl;
    private String transmission;
    private int seats;
    private String fuelType;
    private String tag;
    private String ownerId;
    private long availableFrom;
    private long availableTo;

    /**
     * Constructs a new Car with the specified details.
     */
    public Car(String id, String name, String type, String location, double pricePerHour, double rating, String imageUrl, String transmission, int seats, String fuelType, String tag, String ownerId, long availableFrom, long availableTo) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.location = location;
        this.pricePerHour = pricePerHour;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.transmission = transmission;
        this.seats = seats;
        this.fuelType = fuelType;
        this.tag = tag;
        this.ownerId = ownerId;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public double getPricePerHour() { return pricePerHour; }
    public double getRating() { return rating; }
    public String getImageUrl() { return imageUrl; }
    public String getTransmission() { return transmission; }
    public int getSeats() { return seats; }
    public String getFuelType() { return fuelType; }
    public String getTag() { return tag; }
    public String getOwnerId() { return ownerId; }
    public long getAvailableFrom() { return availableFrom; }
    public long getAvailableTo() { return availableTo; }

    public void setId(String id) { this.id = id; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setAvailableFrom(long availableFrom) { this.availableFrom = availableFrom; }
    public void setAvailableTo(long availableTo) { this.availableTo = availableTo; }
}
