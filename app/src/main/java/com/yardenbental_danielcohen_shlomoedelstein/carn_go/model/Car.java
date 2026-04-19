package com.yardenbental_danielcohen_shlomoedelstein.carn_go.model;

import java.io.Serializable;

public class Car implements Serializable {
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

    public Car(String name, String type, String location, double pricePerHour, double rating, String imageUrl, String transmission, int seats, String fuelType, String tag) {
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
    }

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
}