package com.smartlife.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserProfileDTO {
    private String id;
    private String nickName;
    private String icon;
    private String introduce;
    private Integer gender;
    private String city;
    private LocalDate birthday;
    private Integer points;
    private Integer level;
}
