package com.smartlife.controller;


import com.smartlife.dto.Result;
import com.smartlife.entity.ShopType;
import com.smartlife.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @GetMapping("list")
    public Result queryTypeList() {
        // 调用业务层带缓存逻辑的方法
        List<ShopType> typeList = typeService.queryTypeList();
        return Result.ok(typeList);
    }
}
