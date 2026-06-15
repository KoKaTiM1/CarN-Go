package com.yardenbental_danielcohen_shlomoedelstein.carn_go.model;

import java.io.Serializable;

/**
 * Model class representing a Car in the system.
 * Implements Serializable to allow passing Car objects between components via Bundles or Intents.
 */
public class Car implements Serializable {
    private String id;
    private String name;
    private String description;
    private String type;
    private String location;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
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
    public Car(String id, String name, String description, String type, String location, double pricePerHour, double rating, String imageUrl, String transmission, int seats, String fuelType, String tag, String ownerId, long availableFrom, long availableTo) {
        this(id, name, description, type, location, null, null, pricePerHour, rating, imageUrl, transmission, seats, fuelType, tag, ownerId, availableFrom, availableTo);
    }

    public Car(String id, String name, String description, String type, String location, Double latitude, Double longitude, double pricePerHour, double rating, String imageUrl, String transmission, int seats, String fuelType, String tag, String ownerId, long availableFrom, long availableTo) {
        this.id = id;
        this.name = name;
        this.description = normalizeDescription(description);
        this.type = type;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
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
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getDistanceKm() { return distanceKm; }
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
    public void setDescription(String description) { this.description = normalizeDescription(description); }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setAvailableFrom(long availableFrom) { this.availableFrom = availableFrom; }
    public void setAvailableTo(long availableTo) { this.availableTo = availableTo; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() <= 100) {
            return trimmed;
        }
        return trimmed.substring(0, 100).trim();
    }
}
