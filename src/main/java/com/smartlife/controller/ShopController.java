package com.smartlife.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlife.dto.Result;
import com.smartlife.entity.Shop;
import com.smartlife.service.IShopService;
import com.smartlife.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
@Validated
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 自己实现缓存更新方法
        return Result.ok(shopService.update(shop));
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1")Integer current,
            @RequestParam(value="x",required = false) Double x,
            @RequestParam(value="y",required = false) Double y)
    {
        return shopService.queryShopByType(typeId,current,x,y);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    @GetMapping("/nearby")
    public Result queryNearbyShops(
            @RequestParam("x")
            @DecimalMin(value = "-180", message = "x must be between -180 and 180")
            @DecimalMax(value = "180", message = "x must be between -180 and 180") Double x,
            @RequestParam("y")
            @DecimalMin(value = "-90", message = "y must be between -90 and 90")
            @DecimalMax(value = "90", message = "y must be between -90 and 90") Double y,
            @RequestParam(value = "radius", defaultValue = "5000")
            @DecimalMin(value = "1", message = "radius must be between 1 and 50000")
            @DecimalMax(value = "50000", message = "radius must be between 1 and 50000") Double radius,
            @RequestParam(value = "typeId", required = false) @Min(value = 1, message = "typeId must be positive") Long typeId,
            @RequestParam(value = "limit", defaultValue = "50")
            @Min(value = 1, message = "limit must be between 1 and 100")
            @Max(value = 100, message = "limit must be between 1 and 100") Integer limit) {
        return Result.ok(shopService.queryNearbyShops(x, y, radius, typeId, limit));
    }
}
