package com.smartlife.dto;

import com.smartlife.entity.Shop;
import lombok.Data;

@Data
public class NearbyShopDTO {
    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Double score;
    private String openHours;
    private Double distance;

    public static NearbyShopDTO from(Shop shop, double distance) {
        NearbyShopDTO dto = new NearbyShopDTO();
        dto.setId(shop.getId());
        dto.setName(shop.getName());
        dto.setTypeId(shop.getTypeId());
        dto.setImages(shop.getImages());
        dto.setArea(shop.getArea());
        dto.setAddress(shop.getAddress());
        dto.setX(shop.getX());
        dto.setY(shop.getY());
        dto.setAvgPrice(shop.getAvgPrice());
        dto.setSold(shop.getSold());
        dto.setComments(shop.getComments());
        dto.setScore(shop.getScore() == null ? null : shop.getScore() / 10D);
        dto.setOpenHours(shop.getOpenHours());
        dto.setDistance(distance);
        return dto;
    }
}
