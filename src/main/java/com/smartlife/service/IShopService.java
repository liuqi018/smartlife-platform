package com.smartlife.service;

import com.smartlife.dto.Result;
import com.smartlife.dto.NearbyShopDTO;
import com.smartlife.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);
    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    List<Shop> searchNearbyShops(Integer typeId, double longitude, double latitude,
                                 double distanceMeters, int page, int pageSize);

    List<NearbyShopDTO> queryNearbyShops(double longitude, double latitude,
                                         double radiusMeters, Long typeId, int limit);
}
