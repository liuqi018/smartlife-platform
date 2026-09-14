package com.smartlife.agent.dto;

import lombok.Data;

@Data
public class ShopToolDto {
    private Long id;
    private String name;
    private Long typeId;
    private String typeName;
    private String area;
    private String address;
    private Long avgPrice;
    private Double score;
    private Integer comments;
    private String openHours;
    private Double distanceMeters;
}
