package com.smartlife.dto;

import lombok.Data;

@Data
public class BlogUpdateDTO {
    private String title;
    private String content;
    private String images;
    private Long shopId;
}
